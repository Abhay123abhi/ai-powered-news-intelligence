package com.example.news.ai;

import java.time.Duration;
import java.util.Optional;

public interface AiResponseCache {
    Optional<AiResponse> get(String key);
    void put(String key, AiResponse response, Duration ttl);
}
