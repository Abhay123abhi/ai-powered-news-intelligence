package com.example.news.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class PublicApiFilterTest {
    @Test void rejectsOversizedJsonIncludingUnknownContentLength() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/ai/brief");
        request.setContent(new byte[32769]);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new PublicApiFilter(new ObjectMapper()).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413); assertThat(chain.getRequest()).isNull();
    }
    @Test void limitsCallerRequestsWithoutTrustingSpoofedForwardingHeaders() throws Exception {
        var filter = new PublicApiFilter(new ObjectMapper());
        MockHttpServletResponse response = null;
        for (int i = 0; i < 9; i++) {
            var request = new MockHttpServletRequest("POST", "/api/ai/brief");
            request.addHeader("X-Forwarded-For", "1.2.3." + i);
            request.setContent("{}".getBytes()); response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
