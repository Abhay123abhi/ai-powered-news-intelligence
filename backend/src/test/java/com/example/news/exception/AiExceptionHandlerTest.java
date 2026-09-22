package com.example.news.exception;

import com.example.news.ai.AiFailure;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiExceptionHandlerTest {
    @Test void responseIncludesDiagnosticMetadataAndRetryHeader() {
        var response = new AiExceptionHandler().aiFailure(new AiFailure("AI_PROVIDER_QUOTA", "Provider returned 429.", 429, 429, 60));
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody().getProperties()).containsEntry("code", "AI_PROVIDER_QUOTA").containsEntry("upstreamStatus", 429).containsEntry("retryAfter", 60);
        assertThat(response.getBody().getProperties().get("requestId")).isNotNull();
    }
    @Test void unexpectedFailuresNeverExposeRawExceptionMessage() {
        var response = new AiExceptionHandler().unexpected(new RuntimeException("secret prompt and API key"));
        assertThat(response.getBody().getDetail()).doesNotContain("secret");
        assertThat(response.getBody().getProperties()).containsEntry("code", "AI_INTERNAL_ERROR");
    }
    @Test void controllerSerializesDiagnosticsAndRejectsInvalidInput() throws Exception {
        var service = org.mockito.Mockito.mock(com.example.news.ai.AiInsightService.class);
        org.mockito.Mockito.when(service.dailyBrief(org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new AiFailure("AI_PROVIDER_QUOTA", "Provider returned 429.", 429, 429, 60));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.example.news.controller.AiController(service))
                .setControllerAdvice(new AiExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/brief")
                .contentType("application/json").content("{\"articles\":[{\"title\":\"Story\"}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("AI_PROVIDER_QUOTA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.upstreamStatus").value(429))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "60"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/brief")
                .contentType("application/json").content("{\"articles\":[]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("AI_INVALID_REQUEST"));
    }
}
