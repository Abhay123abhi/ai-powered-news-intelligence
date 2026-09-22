package com.example.news.ai;

import com.example.news.model.NewsArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiReliabilityTest {
    private static final String JSON = "{\"sections\":[{\"heading\":\"Answer\",\"items\":[{\"text\":\"Supported\",\"sourceIds\":[1]}]}]}";
    private NewsArticle article(String source, String date) {
        return new NewsArticle("Title", "Description", "https://example.com/story", source, date, null);
    }
    private AiInsightService service(AiProvider provider, AiResponseCache cache) {
        when(provider.isConfigured()).thenReturn(true); when(provider.modelName()).thenReturn("test-model");
        return new AiInsightService(provider, cache, new ObjectMapper(), true, Duration.ofMinutes(30));
    }
    @Test void doesNotExposeClaimsWithOnlyInventedCitations() {
        var provider = mock(AiProvider.class); var cache = mock(AiResponseCache.class);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(provider.generate(anyString(), anyString())).thenReturn(JSON.replace("[1]", "[99]"));
        var response = service(provider, cache).dailyBrief(List.of(article("Guardian", "2026-09-22")));
        assertThat(response.text()).doesNotContain("Supported").contains("not provide enough cited evidence");
        assertThat(response.citations()).isEmpty();
    }
    @Test void metadataChangesInvalidateTheCacheKey() {
        var provider = mock(AiProvider.class); var cache = mock(AiResponseCache.class);
        Set<String> keys = new HashSet<>();
        when(cache.get(anyString())).thenAnswer(invocation -> { keys.add(invocation.getArgument(0)); return Optional.empty(); });
        when(provider.generate(anyString(), anyString())).thenReturn(JSON);
        var service = service(provider, cache);
        service.dailyBrief(List.of(article("Guardian", "2026-09-22")));
        service.dailyBrief(List.of(article("NYT", "2026-09-22")));
        service.dailyBrief(List.of(article("Guardian", "2026-09-23")));
        assertThat(keys).hasSize(3);
    }
    @Test void coalescesSimultaneousIdenticalGenerations() throws Exception {
        var provider = mock(AiProvider.class); var cache = mock(AiResponseCache.class);
        CountDownLatch generating = new CountDownLatch(1), secondLookup = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        Map<String, AiResponse> saved = new ConcurrentHashMap<>();
        when(cache.get(anyString())).thenAnswer(invocation -> {
            if (lookups.incrementAndGet() >= 3) secondLookup.countDown();
            return Optional.ofNullable(saved.get(invocation.getArgument(0)));
        });
        doAnswer(invocation -> { saved.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(cache).put(anyString(), any(), any());
        when(provider.generate(anyString(), anyString())).thenAnswer(invocation -> {
            generating.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return JSON;
        });
        var service = service(provider, cache); var articles = List.of(article("Guardian", "2026-09-22"));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.dailyBrief(articles));
            assertThat(generating.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.dailyBrief(articles));
            assertThat(secondLookup.await(5, TimeUnit.SECONDS)).isTrue(); release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).text()).isEqualTo(second.get(5, TimeUnit.SECONDS).text());
        } finally { release.countDown(); }
        verify(provider, times(1)).generate(anyString(), anyString());
    }
}
