package com.example.news.ai;

import com.example.news.model.NewsArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AiRequestReliabilityTest {
    static final List<NewsArticle> ARTICLES = List.of(new NewsArticle("Story", "Excerpt", "https://example.com", "Guardian", "today", null));
    static final String RESPONSE = "{\"sections\":[{\"heading\":\"Brief\",\"items\":[{\"text\":\"A fact\",\"sourceIds\":[1]}]}]}";
    AiResponseCache cache = new AiResponseCache() {
        final Map<String, AiResponse> values = new ConcurrentHashMap<>();
        public Optional<AiResponse> get(String key) { return Optional.ofNullable(values.get(key)).map(r -> r.withCached(true)); }
        public void put(String key, AiResponse value, Duration ttl) { values.put(key, value); }
    };
    private AiInsightService service(java.util.function.Supplier<String> action, int limit) {
        AiProvider provider = new AiProvider() {
            public String generate(String system, String prompt) { return action.get(); }
            public String modelName() { return "test"; }
            public boolean isConfigured() { return true; }
        };
        return new AiInsightService(provider, cache, new ObjectMapper(), true, limit, Duration.ofMinutes(30));
    }
    @Test void cachedAnswerStillWorksAfterApplicationLimit() {
        var service = service(() -> RESPONSE, 1);
        service.dailyBrief(ARTICLES);
        assertThatThrownBy(() -> service.ask("Why?", ARTICLES)).isInstanceOfSatisfying(AiFailure.class, e -> {
            assertThat(e.code()).isEqualTo("AI_APP_RATE_LIMIT");
            assertThat(e.retryAfter()).isBetween(1, 60);
        });
        assertThat(service.dailyBrief(ARTICLES).cached()).isTrue();
    }
    @Test void concurrentRequestIsRejectedAndLockIsReleased() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var service = service(() -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            return RESPONSE;
        }, 15);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.dailyBrief(ARTICLES));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.ask("Why?", ARTICLES)).isInstanceOfSatisfying(AiFailure.class,
                        e -> assertThat(e.code()).isEqualTo("AI_BUSY"));
            } finally { release.countDown(); }
            assertThat(first.get(5, TimeUnit.SECONDS).text()).contains("A fact");
            assertThat(service.ask("Why?", ARTICLES).text()).contains("A fact");
        }
    }
    @Test void malformedResponseIsDiagnosableAndDoesNotLeaveServerBusy() {
        var service = service(() -> "invalid JSON secret data", 15);
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> service.dailyBrief(ARTICLES))
                .isInstanceOfSatisfying(AiFailure.class, e -> {
                    assertThat(e.code()).isEqualTo("AI_INVALID_RESPONSE");
                    assertThat(e.getMessage()).doesNotContain("secret");
                });
    }
}
