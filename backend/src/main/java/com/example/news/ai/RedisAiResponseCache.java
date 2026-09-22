package com.example.news.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisAiResponseCache implements AiResponseCache {

    private static final String PREFIX = "ai:insight:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAiResponseCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AiResponse> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + key);
            if (value == null || value.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(value, AiResponse.class).withCached(true));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, AiResponse response, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    PREFIX + key,
                    objectMapper.writeValueAsString(response.withCached(false)),
                    ttl
            );
        } catch (JsonProcessingException ignored) {
            // AI should remain usable even if a response cannot be cached.
        } catch (RuntimeException ignored) {
            // Redis outages degrade to an uncached AI request rather than breaking the feature.
        }
    }
}
