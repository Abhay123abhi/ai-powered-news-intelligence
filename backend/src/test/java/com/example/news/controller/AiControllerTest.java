package com.example.news.controller;

import com.example.news.ai.AiInsightService;
import com.example.news.exception.*;
import com.example.news.service.FeedStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerTest {
    @Test void rejectsArbitraryArticlePayloadsBeforeGeneration() throws Exception {
        var insights = mock(AiInsightService.class);
        mvc(insights, mock(FeedStore.class)).perform(post("/api/ai/brief").contentType(MediaType.APPLICATION_JSON)
                .content("{\"articles\":[{\"title\":\"injected content\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(insights);
    }
    @Test void mapsExpiredFeedAndQuotaToActionableResponses() throws Exception {
        var feeds = mock(FeedStore.class);
        when(feeds.resolve(anyString(), anyInt(), anyList())).thenThrow(ApiException.expired(), ApiException.quota(60));
        var mvc = mvc(mock(AiInsightService.class), feeds);
        String body = "{\"feedId\":\"12345678-1234-1234-1234-123456789abc\",\"page\":1,\"articleIds\":[0]}";
        mvc.perform(post("/api/ai/brief").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("FEED_EXPIRED"));
        mvc.perform(post("/api/ai/brief").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }
    private MockMvc mvc(AiInsightService insights, FeedStore feeds) {
        return MockMvcBuilders.standaloneSetup(new AiController(insights, feeds))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
