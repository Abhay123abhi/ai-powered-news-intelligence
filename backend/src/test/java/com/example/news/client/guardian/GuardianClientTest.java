package com.example.news.client.guardian;

import com.example.news.exception.NewsProviderException;
import com.example.news.model.NewsApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardianClientTest {

    private final GuardianFeignClient feignClient = mock(GuardianFeignClient.class);
    private final GuardianClient client = new GuardianClient(feignClient);

    @Test
    void requestsNewestArticlesWithoutAQueryForLatestNews() {
        ReflectionTestUtils.setField(client, "apiKey", "guardian-key");
        when(feignClient.search(isNull(), eq(1), eq(10), any(), eq("newest"), eq("published"), eq("guardian-key")))
                .thenReturn(Map.of("response", Map.of(
                        "total", 1,
                        "pages", 1,
                        "results", List.of(Map.of(
                                "webTitle", "Latest headline",
                                "webUrl", "https://example.com/guardian",
                                "webPublicationDate", "2026-08-25T10:00:00Z"
                        ))
                )));

        NewsApiResult result = client.search(null, 1, 10);

        assertThat(result.articles()).hasSize(1);
        assertThat(result.articles().getFirst().title()).isEqualTo("Latest headline");
        verify(feignClient).search(null, 1, 10, "thumbnail,trailText", "newest", "published", "guardian-key");
    }

    @Test
    void requestsRelevantArticlesForKeywordSearch() {
        ReflectionTestUtils.setField(client, "apiKey", "guardian-key");
        when(feignClient.search(eq("climate"), eq(1), eq(10), any(), eq("relevance"), eq("published"), eq("guardian-key")))
                .thenReturn(Map.of("response", Map.of(
                        "total", 1,
                        "pages", 1,
                        "results", List.of(Map.of(
                                "webTitle", "Climate policy update",
                                "webUrl", "https://example.com/climate",
                                "webPublicationDate", "2026-09-16T10:00:00Z"
                        ))
                )));

        NewsApiResult result = client.search("climate", 1, 10);

        assertThat(result.articles()).hasSize(1);
        verify(feignClient).search("climate", 1, 10, "thumbnail,trailText", "relevance", "published", "guardian-key");
    }

    @Test
    void reportsMissingApiKeyAsProviderFailure() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.search("java", 1, 10))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("not configured");
    }
}
