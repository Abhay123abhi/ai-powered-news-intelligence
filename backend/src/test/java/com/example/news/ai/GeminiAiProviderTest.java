package com.example.news.ai;

import com.example.news.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiAiProviderTest {
    @Test void doesNotRetryQuotaErrorsAndHonorsRetryAfter() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        AiBudget budget = mock(AiBudget.class);
        var provider = provider(builder, budget);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
        assertThatThrownBy(() -> provider.generate("system", "question")).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS); assertThat(ex.retryAfter).isEqualTo(120);
        });
        verify(budget, times(1)).acquire(); server.verify();
    }
    @Test void countsEachRetryAndReturnsTextAfterTransientFailure() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        AiBudget budget = mock(AiBudget.class);
        var provider = provider(builder, budget);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent"))
                .andRespond(withServerError());
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}", MediaType.APPLICATION_JSON));
        assertThat(provider.generate("system", "question")).isEqualTo("ok");
        verify(budget, times(2)).acquire(); server.verify();
    }
    private GeminiAiProvider provider(RestClient.Builder builder, AiBudget budget) {
        return new GeminiAiProvider(builder.baseUrl("https://generativelanguage.googleapis.com").build(), budget, "test-key", "test-model", 1, Duration.ofMillis(1), 4, Duration.ofSeconds(30));
    }
}
