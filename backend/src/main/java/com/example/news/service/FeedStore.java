package com.example.news.service;

import com.example.news.exception.ApiException;
import com.example.news.model.NewsArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/** Bounded local fallback for one Render instance; Redis preserves sessions across restarts. */
@Service
public class FeedStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final Cache<String, Feed> feeds;
    private final Cache<String, String> queries;

    public FeedStore(StringRedisTemplate redis, ObjectMapper mapper,
                     @Value("${news.feed-ttl:15m}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
        feeds = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(ttl).build();
        queries = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(ttl).build();
    }

    public synchronized Feed start(String key, String query, int size) {
        String id = queries.getIfPresent(key);
        try { if (id == null) id = redis.opsForValue().get("news:query:v3:" + key); }
        catch (RuntimeException ignored) { }
        if (id != null) {
            try { return get(id); } catch (ApiException ignored) { }
        }
        Feed feed = new Feed();
        feed.id = UUID.randomUUID().toString();
        feed.query = query;
        feed.pageSize = size;
        feed.createdAt = System.currentTimeMillis();
        feed.queryKey = key;
        feeds.put(feed.id, feed);
        queries.put(key, feed.id);
        return feed;
    }

    public Feed get(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw ApiException.expired();
        Feed feed = feeds.getIfPresent(id);
        if (feed == null) {
            try {
                String json = redis.opsForValue().get("news:feed:v3:" + id);
                if (json != null) {
                    Feed restored = mapper.readValue(json, Feed.class);
                    feed = feeds.get(id, ignored -> restored);
                }
            } catch (Exception ignored) { }
        }
        if (feed == null || System.currentTimeMillis() - feed.createdAt >= ttl.toMillis()) {
            feeds.invalidate(id);
            throw ApiException.expired();
        }
        return feed;
    }

    public void save(Feed feed) {
        long remaining = ttl.toMillis() - (System.currentTimeMillis() - feed.createdAt);
        if (remaining <= 0) throw ApiException.expired();
        feeds.put(feed.id, feed);
        try {
            Duration expires = Duration.ofMillis(remaining);
            redis.opsForValue().set("news:feed:v3:" + feed.id, mapper.writeValueAsString(feed), expires);
            redis.opsForValue().set("news:query:v3:" + feed.queryKey, feed.id, expires);
        } catch (Exception ignored) { /* Bounded local feed remains usable. AI quota still fails closed. */ }
    }

    public List<NewsArticle> resolve(String id, int page, List<Integer> articleIds) {
        Feed feed = get(id);
        synchronized (feed) {
            if (!feed.servedPages.contains(page)) throw ApiException.expired();
            int offset = (page - 1) * feed.pageSize;
            List<NewsArticle> visible = feed.articles.subList(Math.min(offset, feed.articles.size()),
                    Math.min(offset + feed.pageSize, feed.articles.size()));
            if (articleIds.stream().distinct().count() != articleIds.size()
                    || articleIds.stream().anyMatch(i -> i == null || i < 0 || i >= visible.size())) {
                throw new IllegalArgumentException("Choose valid, distinct stories from the current page.");
            }
            return articleIds.stream().sorted().map(visible::get).toList();
        }
    }

    public static class Feed {
        public String id;
        public String queryKey;
        public String query;
        public int pageSize;
        public long createdAt;
        public List<NewsArticle> articles = new ArrayList<>();
        public Map<String, Integer> nextPages = new LinkedHashMap<>();
        public Set<String> exhausted = new HashSet<>();
        public Set<String> unavailable = new LinkedHashSet<>();
        public Set<Integer> servedPages = new HashSet<>();
        public boolean initialized;
        public boolean limited;
    }
}
