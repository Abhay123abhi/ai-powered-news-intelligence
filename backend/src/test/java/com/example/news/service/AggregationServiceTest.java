package com.example.news.service;

import com.example.news.client.NewsProviderClient;
import com.example.news.exception.*;
import com.example.news.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AggregationServiceTest {
    ExecutorService executor;
    FeedStore store;
    NewsProviderClient guardian, nyt;
    AggregationService service;
    @BeforeEach void setup() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        store = new FeedStore(mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS), new ObjectMapper(), Duration.ofMinutes(15));
        guardian = mock(NewsProviderClient.class); nyt = mock(NewsProviderClient.class);
        when(guardian.getProviderName()).thenReturn("Guardian"); when(nyt.getProviderName()).thenReturn("NYT");
        service = new AggregationService(List.of(guardian, nyt), store, executor);
        ReflectionTestUtils.setField(service, "providerTimeout", Duration.ofSeconds(1));
        ReflectionTestUtils.setField(service, "guardianEnabled", true);
        ReflectionTestUtils.setField(service, "nytEnabled", true);
        ReflectionTestUtils.setField(service, "maxProviderPages", 5);
    }
    @AfterEach void close() { executor.shutdownNow(); }
    List<NewsArticle> articles(String source, int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> new NewsArticle(source + i, "Description", "https://example.com/" + source + i, source, "2026-09-22T10:00:00Z", null)).toList();
    }
    @Test void retainsEveryArticleAcrossMergedPagesAndKeepsPreviousPageStable() {
        when(guardian.search(null, 1, 10)).thenReturn(new NewsApiResult(10, 1, articles("Guardian", 0, 10)));
        when(nyt.search(null, 1, 10)).thenReturn(new NewsApiResult(10, 1, articles("NYT", 0, 10)));
        var first = service.search("latest", 1, 12, null);
        var second = service.search("latest", 2, 12, first.feedId());
        assertThat(first.articles()).hasSize(12); assertThat(second.articles()).hasSize(8);
        Set<String> urls = new HashSet<>(); first.articles().forEach(a -> urls.add(a.url())); second.articles().forEach(a -> urls.add(a.url()));
        assertThat(urls).hasSize(20); assertThat(second.nextPage()).isNull();
        assertThat(service.search("latest", 1, 12, first.feedId()).articles()).isEqualTo(first.articles());
        verify(guardian, times(1)).search(null, 1, 10);
    }
    @Test void carriesOverflowBeforeFetchingNextProviderBatch() {
        when(guardian.search("java", 1, 10)).thenReturn(new NewsApiResult(20, 2, articles("Guardian", 0, 10)));
        when(guardian.search("java", 2, 10)).thenReturn(new NewsApiResult(20, 2, articles("Guardian", 10, 20)));
        when(nyt.search("java", 1, 10)).thenReturn(new NewsApiResult(10, 1, articles("NYT", 0, 10)));
        var first = service.search("java", 1, 12, null);
        var second = service.search("java", 2, 12, first.feedId());
        var third = service.search("java", 3, 12, first.feedId());
        assertThat(second.articles()).hasSize(12); assertThat(third.articles()).hasSize(6);
        assertThat(third.nextPage()).isNull();
        verify(nyt, never()).search("java", 2, 10);
    }
    @Test void exposesPartialSourceFailure() {
        when(guardian.search(null, 1, 10)).thenReturn(new NewsApiResult(1, 1, articles("Guardian", 0, 1)));
        when(nyt.search(null, 1, 10)).thenThrow(new NewsProviderException("failure"));
        var result = service.search(null, 1, 12, null);
        assertThat(result.partial()).isTrue(); assertThat(result.unavailableSources()).containsExactly("NYT");
    }
    @Test void reportsUnavailableWhenBothProvidersFail() {
        when(guardian.search(null, 1, 10)).thenThrow(new NewsProviderException("failure"));
        when(nyt.search(null, 1, 10)).thenThrow(new NewsProviderException("failure"));
        assertThatThrownBy(() -> service.search(null, 1, 12, null)).isInstanceOf(NewsUnavailableException.class);
    }
    @Test void resolvesOnlyStoriesFromAServedPage() {
        when(guardian.search(null, 1, 10)).thenReturn(new NewsApiResult(10, 1, articles("Guardian", 0, 10)));
        when(nyt.search(null, 1, 10)).thenReturn(new NewsApiResult(0, 0, List.of()));
        var result = service.search(null, 1, 8, null);
        assertThat(store.resolve(result.feedId(), 1, List.of(0))).containsExactly(result.articles().getFirst());
        assertThatThrownBy(() -> store.resolve(result.feedId(), 2, List.of(0))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store.resolve(result.feedId(), 1, List.of(8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cachesEmptyResultsAndDoesNotRepeatProviderCalls() {
        when(guardian.search(null, 1, 10)).thenReturn(new NewsApiResult(0, 0, List.of()));
        when(nyt.search(null, 1, 10)).thenReturn(new NewsApiResult(0, 0, List.of()));
        service.search(null, 1, 12, null); var result = service.search(null, 1, 12, null);
        assertThat(result.articles()).isEmpty(); assertThat(result.nextPage()).isNull();
        verify(guardian, times(1)).search(null, 1, 10);
    }
}
