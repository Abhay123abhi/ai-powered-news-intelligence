package com.example.news.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** No caller-controlled forwarding headers are trusted. Global budgets remain authoritative. */
@Component
public class PublicApiFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final Cache<String, AtomicInteger> windows = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(2)).build();
    public PublicApiFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean ai = path.startsWith("/api/ai/") && "POST".equals(request.getMethod());
        boolean news = "/api/news".equals(path) && "GET".equals(request.getMethod());
        if (!ai && !news) { chain.doFilter(request, response); return; }
        long minute = System.currentTimeMillis() / 60000;
        String bucket = (ai ? "ai:" : "news:") + minute;
        int perClient = ai ? 8 : 30;
        int total = ai ? 30 : 60;
        if (windows.get(bucket + ":all", k -> new AtomicInteger()).incrementAndGet() > total
                || windows.get(bucket + ":" + request.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet() > perClient) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "REQUEST_LIMIT", "Please pause for a minute before trying again.");
            return;
        }
        if (!ai) { chain.doFilter(request, response); return; }
        // Read a bounded body even for chunked requests; Spring's form limit does not cover JSON.
        byte[] body = request.getInputStream().readNBytes(32769);
        if (body.length > 32768) {
            reject(response, 413, "REQUEST_TOO_LARGE", "This request is too large. Choose fewer stories.");
            return;
        }
        HttpServletRequestWrapper bounded = new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        chain.doFilter(bounded, response);
    }
    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("status", status, "code", code, "detail", message,
                "retryAfter", status == 429 ? 60 : 0));
    }
}
