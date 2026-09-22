package com.example.news.service;

import com.example.news.client.NewsProviderClient;
import com.example.news.exception.NewsUnavailableException;
import com.example.news.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AggregationService {
    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is", "of", "on", "or", "the", "to", "with");
    private final List<NewsProviderClient> providers;
    private final FeedStore store;
    private final ExecutorService providerExecutor;
    @Value("${news.provider-timeout:25s}") private Duration providerTimeout;
    @Value("${news.guardian.enabled:true}") private boolean guardianEnabled;
    @Value("${news.nyt.enabled:true}") private boolean nytEnabled;
    @Value("${news.max-provider-pages:5}") private int maxProviderPages;

    public AggregationService(List<NewsProviderClient> providers, FeedStore store, ExecutorService providerExecutor) {
        this.providers = providers;
        this.store = store;
        this.providerExecutor = providerExecutor;
    }

    public SearchResponse search(String keyword, int page, int pageSize, String feedId) {
        long started = System.currentTimeMillis();
        String query = normalizeSearchKeyword(keyword);
        if (page < 1 || pageSize < 1 || pageSize > 25 || query.length() > 120) {
            throw new IllegalArgumentException("Use a query up to 120 characters and 1–25 stories per page.");
        }
        if (feedId == null && page != 1) throw com.example.news.exception.ApiException.expired();
        String key = UUID.nameUUIDFromBytes((query.toLowerCase(Locale.ROOT) + ":" + pageSize)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        FeedStore.Feed feed = feedId == null ? store.start(key, query, pageSize) : store.get(feedId);
        synchronized (feed) {
            if (!feed.query.equalsIgnoreCase(query) || feed.pageSize != pageSize) {
                throw new IllegalArgumentException("Start a new search when changing the topic or page size.");
            }
            int lastServed = feed.servedPages.stream().mapToInt(Integer::intValue).max().orElse(0);
            if (page > lastServed + 1) throw new IllegalArgumentException("Browse pages in order.");
            if (!feed.initialized) {
                providers.stream().filter(p -> isProviderEnabled(p.getProviderName()))
                        .forEach(p -> feed.nextPages.put(p.getProviderName(), 1));
                if (feed.nextPages.isEmpty()) throw new NewsUnavailableException("News sources are temporarily unavailable.");
                feed.initialized = true;
            }
            // Append ranked batches; never reorder a page already shown or discard overflow.
            while (feed.articles.size() < page * pageSize && hasMore(feed)) fetchBatch(feed);
            int from = Math.min((page - 1) * pageSize, feed.articles.size());
            int to = Math.min(from + pageSize, feed.articles.size());
            feed.servedPages.add(page);
            store.save(feed);
            boolean next = to < feed.articles.size() || hasMore(feed);
            return new SearchResponse(query, feed.id, page, pageSize, page > 1 ? page - 1 : null,
                    next ? page + 1 : null, System.currentTimeMillis() - started,
                    Instant.ofEpochMilli(feed.createdAt).toString(), !feed.unavailable.isEmpty(),
                    List.copyOf(feed.unavailable), feed.limited, List.copyOf(feed.articles.subList(from, to)));
        }
    }

    private boolean hasMore(FeedStore.Feed feed) {
        return feed.nextPages.keySet().stream().anyMatch(p -> !feed.exhausted.contains(p));
    }

    private void fetchBatch(FeedStore.Feed feed) {
        Map<String, CompletableFuture<NewsApiResult>> tasks = new LinkedHashMap<>();
        for (NewsProviderClient provider : providers) {
            String name = provider.getProviderName();
            if (!feed.nextPages.containsKey(name) || feed.exhausted.contains(name)) continue;
            int upstreamPage = feed.nextPages.get(name);
            tasks.put(name, CompletableFuture.supplyAsync(() -> provider.search(
                    "latest".equals(feed.query) ? null : feed.query, upstreamPage, 10), providerExecutor)
                    .orTimeout(providerTimeout.toMillis(), TimeUnit.MILLISECONDS));
        }
        List<NewsArticle> batch = new ArrayList<>();
        int successes = 0;
        for (var task : tasks.entrySet()) {
            String name = task.getKey();
            try {
                NewsApiResult result = task.getValue().join();
                successes++;
                batch.addAll(result.articles());
                int current = feed.nextPages.get(name);
                if (result.articles().isEmpty() || current >= result.totalPages() || current >= maxProviderPages) {
                    feed.exhausted.add(name);
                    if (current >= maxProviderPages && current < result.totalPages()) feed.limited = true;
                }
                feed.nextPages.put(name, current + 1);
            } catch (CompletionException ex) {
                feed.unavailable.add(name);
                feed.exhausted.add(name);
                log.warn("News source {} unavailable ({})", name, ex.getCause().getClass().getSimpleName());
            }
        }
        if (successes == 0 && feed.articles.isEmpty()) {
            // Allow a subsequent user retry; do not preserve a permanently failed empty session.
            feed.exhausted.clear();
            feed.unavailable.clear();
            throw new NewsUnavailableException("The news sources are taking a break. Please try again shortly.");
        }
        Set<String> urls = new HashSet<>();
        feed.articles.forEach(a -> urls.add(normalizeUrl(a.url())));
        Comparator<NewsArticle> order = "latest".equals(feed.query) ? newestFirst() : keywordRelevanceOrder(feed.query);
        batch.stream().filter(a -> a != null && a.url() != null && a.url().startsWith("https://"))
                .sorted(order).filter(a -> urls.add(normalizeUrl(a.url()))).forEach(feed.articles::add);
    }

    private Comparator<NewsArticle> newestFirst() {
        return Comparator.comparing(this::publishedInstant, Comparator.reverseOrder());
    }

    private Comparator<NewsArticle> keywordRelevanceOrder(String query) {
        return Comparator
                .comparingInt((NewsArticle article) -> relevanceScore(article, query))
                .reversed()
                .thenComparing(newestFirst());
    }

    private int relevanceScore(NewsArticle article, String query) {
        String title = normalizeText(article.title());
        String description = normalizeText(article.description());
        String normalizedQuery = normalizeText(query);
        int score = 0;

        if (!normalizedQuery.isBlank()) {
            if (title.contains(normalizedQuery)) score += 12;
            if (description.contains(normalizedQuery)) score += 6;
        }

        for (String term : searchTerms(normalizedQuery)) {
            if (containsWord(title, term)) score += 4;
            if (containsWord(description, term)) score += 2;
        }

        return score;
    }

    private List<String> searchTerms(String query) {
        return Arrays.stream(query.split("\\s+"))
                .map(term -> term.replaceAll("[^a-z0-9]", ""))
                .filter(term -> term.length() >= 2)
                .filter(term -> !SEARCH_STOP_WORDS.contains(term))
                .distinct()
                .toList();
    }

    private boolean containsWord(String text, String term) {
        if (text.isBlank() || term.isBlank()) return false;
        return Arrays.stream(text.split("[^a-z0-9]+"))
                .anyMatch(term::equals);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean isProviderEnabled(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "guardian" -> guardianEnabled;
            case "nyt" -> nytEnabled;
            default -> true;
        };
    }

    private String normalizeUrl(String url) {
        String s = url.trim();
        int queryIdx = s.indexOf('?');
        if (queryIdx > 0) s = s.substring(0, queryIdx);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "latest";
        }

        String normalized = keyword.trim();
        return normalized.equalsIgnoreCase("latest-news") || normalized.equalsIgnoreCase("latest")
                ? "latest"
                : normalized;
    }

    private Instant publishedInstant(NewsArticle article) {
        try { return java.time.OffsetDateTime.parse(article.publishedAt()).toInstant(); }
        catch (Exception ignored) { return Instant.MIN; }
    }

}
