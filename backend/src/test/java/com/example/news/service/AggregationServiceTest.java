package com.example.news.service;

import com.example.news.client.NewsProviderClient;
import com.example.news.exception.NewsProviderException;
import com.example.news.exception.NewsUnavailableException;
import com.example.news.model.NewsApiResult;
import com.example.news.model.NewsArticle;
import com.example.news.model.SearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock
    private NewsProviderClient provider;

    @Mock
    private CacheService cacheService;

    private ExecutorService executor;
    private AggregationService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        service = new AggregationService(List.of(provider), cacheService, executor);
        ReflectionTestUtils.setField(service, "providerTimeout", Duration.ofSeconds(2));
        ReflectionTestUtils.setField(service, "guardianEnabled", true);
        ReflectionTestUtils.setField(service, "nytEnabled", true);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void returnsProviderTotalsAndNextPageWhenMorePagesExist() {
        NewsArticle article = article("https://example.com/first");
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("java", 1, 12)).thenReturn(new NewsApiResult(36, 3, List.of(article)));

        SearchResponse response = service.search("java", 1, 12);

        assertThat(response.totalArticles()).isEqualTo(36);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.nextPage()).isEqualTo(2);
        verify(cacheService).save("java", 1, 12, new NewsApiResult(36, 3, List.of(article)));
    }

    @Test
    void ranksKeywordMatchesAheadOfNewerUnrelatedVisibleText() {
        NewsArticle unrelated = new NewsArticle(
                "Global politics update",
                "Latest diplomatic developments",
                "https://example.com/unrelated",
                "The Guardian",
                "2026-09-16T12:00:00Z",
                null
        );
        NewsArticle relevant = new NewsArticle(
                "Climate policy update",
                "New climate rules for power plants",
                "https://example.com/climate",
                "The Guardian",
                "2026-09-16T10:00:00Z",
                null
        );

        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("climate", 1, 12))
                .thenReturn(new NewsApiResult(2, 1, List.of(unrelated, relevant)));

        SearchResponse response = service.search("climate", 1, 12);

        assertThat(response.articles()).containsExactly(relevant, unrelated);
    }

    @Test
    void doesNotExposeNextPageOnLastProviderPage() {
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("java", 3, 12))
                .thenReturn(new NewsApiResult(36, 3, List.of(article("https://example.com/last"))));

        SearchResponse response = service.search("java", 3, 12);

        assertThat(response.prevPage()).isEqualTo(2);
        assertThat(response.nextPage()).isNull();
    }

    @Test
    void returnsEmptyResultWhenProviderSuccessfullyFindsNothing() {
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("java", 1, 12)).thenReturn(new NewsApiResult(0, 0, List.of()));

        SearchResponse response = service.search("java", 1, 12);

        assertThat(response.articles()).isEmpty();
        assertThat(response.totalArticles()).isZero();
        assertThat(response.nextPage()).isNull();
    }

    @Test
    void returnsCachedResultWithoutCallingProviders() {
        NewsArticle article = article("https://example.com/cached");
        when(cacheService.load("java", 1, 12))
                .thenReturn(new NewsApiResult(36, 3, List.of(article)));

        SearchResponse response = service.search("java", 1, 12);

        assertThat(response.articles()).containsExactly(article);
        assertThat(response.totalArticles()).isEqualTo(36);
        assertThat(response.nextPage()).isEqualTo(2);
        verifyNoInteractions(provider);
    }

    @Test
    void latestNewsAliasRequestsProviderNativeLatestArticles() {
        NewsArticle article = article("https://example.com/latest");
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search(null, 1, 12))
                .thenReturn(new NewsApiResult(36, 3, List.of(article)));

        SearchResponse response = service.search("latest-news", 1, 12);

        assertThat(response.articles()).containsExactly(article);
        assertThat(response.searchKeyword()).isEqualTo("latest");
        verify(provider).search(null, 1, 12);
    }

    @Test
    void continuesWithLiveProvidersWhenRedisIsUnavailable() {
        NewsArticle article = article("https://example.com/live");
        when(cacheService.load("java", 1, 12))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("java", 1, 12))
                .thenReturn(new NewsApiResult(12, 1, List.of(article)));

        SearchResponse response = service.search("java", 1, 12);

        assertThat(response.articles()).containsExactly(article);
    }

    @Test
    void returnsServiceUnavailableWhenEveryProviderFails() {
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("java", 1, 12))
                .thenThrow(new NewsProviderException("Guardian API key is not configured"));

        assertThatThrownBy(() -> service.search("java", 1, 12))
                .isInstanceOf(NewsUnavailableException.class)
                .hasMessageContaining("GUARDIAN_API_KEY");
    }

    @Test
    void explainsWhenEveryProviderTimesOut() {
        ReflectionTestUtils.setField(service, "providerTimeout", Duration.ofMillis(25));
        when(provider.getProviderName()).thenReturn("Guardian");
        when(provider.search("java", 1, 12)).thenAnswer(invocation -> {
            Thread.sleep(250);
            return new NewsApiResult(1, 1, List.of(article("https://example.com/slow")));
        });

        assertThatThrownBy(() -> service.search("java", 1, 12))
                .isInstanceOf(NewsUnavailableException.class)
                .hasMessageContaining("timed out")
                .hasMessageContaining("NEWS_PROVIDER_TIMEOUT");
    }

    private NewsArticle article(String url) {
        return new NewsArticle("Headline", "Description", url, "The Guardian", "2026-08-23T10:00:00Z", null);
    }
}
