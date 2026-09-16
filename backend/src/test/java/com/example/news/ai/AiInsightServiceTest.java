package com.example.news.ai;

import com.example.news.model.NewsArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiInsightServiceTest {

    @Test
    void briefUsesProviderCachesResponseAndValidatesCitations() {
        AtomicInteger calls = new AtomicInteger();
        AiProvider provider = new AiProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                assertThat(systemPrompt).contains("Use ONLY the supplied article");
                assertThat(userPrompt).contains("Guardian").contains("Example headline");
                return """
                        {
                          "sections": [
                            {
                              "heading": "Key developments",
                              "items": [
                                {"text": "Example development", "sourceIds": [1, 99]}
                              ]
                            }
                          ]
                        }
                        """;
            }

            @Override
            public String modelName() {
                return "test-model";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }
        };

        Map<String, AiResponse> store = new ConcurrentHashMap<>();
        AiResponseCache cache = new AiResponseCache() {
            @Override
            public Optional<AiResponse> get(String key) {
                return Optional.ofNullable(store.get(key)).map(response -> response.withCached(true));
            }

            @Override
            public void put(String key, AiResponse response, Duration ttl) {
                store.put(key, response.withCached(false));
            }
        };

        AiInsightService service = new AiInsightService(
                provider,
                cache,
                new ObjectMapper(),
                true,
                15,
                Duration.ofMinutes(30)
        );

        List<NewsArticle> articles = List.of(new NewsArticle(
                "Example headline", "Example description", "https://example.com/story",
                "Guardian", "2026-08-26T00:00:00Z", null));

        AiResponse first = service.dailyBrief(articles);
        AiResponse second = service.dailyBrief(articles);

        assertThat(first.cached()).isFalse();
        assertThat(first.text()).contains("Example development").contains("[1]");
        assertThat(first.content().sections().getFirst().items().getFirst().sourceIds()).containsExactly(1);
        assertThat(first.citations()).hasSize(1);
        assertThat(first.citations().getFirst().url()).isEqualTo("https://example.com/story");
        assertThat(second.cached()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }
}
