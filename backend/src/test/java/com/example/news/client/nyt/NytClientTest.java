package com.example.news.client.nyt;

import com.example.news.exception.NewsProviderException;
import com.example.news.model.NewsApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NytClientTest {

    private final NytFeignClient feignClient = mock(NytFeignClient.class);
    private final NytClient client = new NytClient(feignClient);

    @Test
    void supportsListBasedMultimediaAndProviderNativeLatestNews() {
        ReflectionTestUtils.setField(client, "apiKey", "nyt-key");
        when(feignClient.search(null, 0, "newest", "nyt-key"))
                .thenReturn(Map.of("response", Map.of(
                        "meta", Map.of("hits", 1),
                        "docs", List.of(Map.of(
                                "headline", Map.of("main", "Latest NYT headline"),
                                "abstract", "Story summary",
                                "web_url", "https://example.com/nyt",
                                "pub_date", "2026-08-25T11:00:00Z",
                                "multimedia", List.of(Map.of("url", "/images/story.jpg"))
                        ))
                )));

        NewsApiResult result = client.search(null, 1, 10);

        assertThat(result.articles()).hasSize(1);
        assertThat(result.articles().getFirst().imageUrl())
                .isEqualTo("https://www.nytimes.com/images/story.jpg");
        verify(feignClient).search(null, 0, "newest", "nyt-key");
    }

    @Test
    void leavesSortUnsetSoNytUsesDefaultRelevanceForKeywordSearch() {
        ReflectionTestUtils.setField(client, "apiKey", "nyt-key");
        when(feignClient.search("climate", 0, null, "nyt-key"))
                .thenReturn(Map.of("response", Map.of(
                        "meta", Map.of("hits", 1),
                        "docs", List.of(Map.of(
                                "headline", Map.of("main", "Climate policy update"),
                                "abstract", "Climate policy story",
                                "web_url", "https://example.com/nyt-climate",
                                "pub_date", "2026-09-16T11:00:00Z",
                                "multimedia", List.of()
                        ))
                )));

        NewsApiResult result = client.search("climate", 1, 10);

        assertThat(result.articles()).hasSize(1);
        verify(feignClient).search("climate", 0, null, "nyt-key");
    }

    @Test
    void reportsMissingApiKeyAsProviderFailure() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.search("java", 1, 10))
                .isInstanceOf(NewsProviderException.class)
                .hasMessageContaining("not configured");
    }
}
