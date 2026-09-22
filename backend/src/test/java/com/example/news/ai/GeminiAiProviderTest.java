package com.example.news.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class GeminiAiProviderTest {
    @Test void quotaDoesNotRetryAndKeepsItsReasonDuringCooldown() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1beta/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120").body("private provider payload"));
        var provider = new GeminiAiProvider(builder.build(), "test-key", 2);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> provider.generate("private prompt", "private article"))
                    .isInstanceOfSatisfying(AiFailure.class, error -> {
                        assertThat(error.code()).isEqualTo("AI_PROVIDER_QUOTA");
                        assertThat(error.upstreamStatus()).isEqualTo(429);
                        assertThat(error.retryAfter()).isBetween(1, 120);
                        assertThat(error.getMessage()).doesNotContain("private", "test-key");
                    });
        }
        server.verify();
    }
    @Test void accessDeniedPreservesStatusWithoutProviderBody() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("secret"));
        var provider = new GeminiAiProvider(builder.build(), "key", 2);
        assertThatThrownBy(() -> provider.generate("", "")).isInstanceOfSatisfying(AiFailure.class, e -> {
            assertThat(e.code()).isEqualTo("AI_ACCESS_DENIED");
            assertThat(e.upstreamStatus()).isEqualTo(403);
            assertThat(e.getMessage()).doesNotContain("secret");
        });
        server.verify();
    }
    @Test void transientFailureRetriesAndReturnsSuccessfulResponse() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withServerError());
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"}]}}]}", MediaType.APPLICATION_JSON));
        assertThat(new GeminiAiProvider(builder.build(), "key", 1).generate("", "")).isEqualTo("answer");
        server.verify();
    }
    @Test void truncatedOutputHasSpecificDiagnosticCode() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new GeminiAiProvider(builder.build(), "key", 0).generate("", ""))
                .isInstanceOfSatisfying(AiFailure.class, e -> assertThat(e.code()).isEqualTo("AI_OUTPUT_LIMIT"));
        server.verify();
    }
}
