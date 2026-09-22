package com.example.news.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GeminiAiProvider implements AiProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final int circuitFailureThreshold;
    private final Duration circuitOpenDuration;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong();
    private final AtomicLong quotaUntil = new AtomicLong();

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiAiProvider(
            RestClient.Builder builder,
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-3.6-flash}") String model,
            @Value("${ai.gemini.connect-timeout:5s}") Duration connectTimeout,
            @Value("${ai.gemini.read-timeout:25s}") Duration readTimeout,
            @Value("${ai.gemini.max-retries:2}") int maxRetries,
            @Value("${ai.gemini.retry-backoff:400ms}") Duration retryBackoff,
            @Value("${ai.gemini.circuit-failure-threshold:4}") int circuitFailureThreshold,
            @Value("${ai.gemini.circuit-open-duration:30s}") Duration circuitOpenDuration) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));

        this.restClient = builder
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoff = retryBackoff;
        this.circuitFailureThreshold = Math.max(1, circuitFailureThreshold);
        this.circuitOpenDuration = circuitOpenDuration;
    }

    GeminiAiProvider(RestClient client, String apiKey, int maxRetries) {
        this.restClient = client;
        this.apiKey = apiKey;
        this.model = "test-model";
        this.maxRetries = maxRetries;
        this.retryBackoff = Duration.ofMillis(1);
        this.circuitFailureThreshold = 4;
        this.circuitOpenDuration = Duration.ofSeconds(30);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new AiFailure("AI_NOT_CONFIGURED", "The AI provider credential is not configured.", 503, null, 0);
        }
        ensureCircuitClosed();

        RuntimeException lastFailure = null;
        int attempts = maxRetries + 1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String result = execute(systemPrompt, userPrompt);
                consecutiveFailures.set(0);
                circuitOpenUntil.set(0);
                return result;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    int delay = retryAfter(e);
                    quotaUntil.accumulateAndGet(System.currentTimeMillis() + delay * 1000L, Math::max);
                    throw new AiFailure("AI_PROVIDER_QUOTA", "The AI provider returned a rate or quota limit (429).", 429, 429, delay);
                }
                if (!isTransient(e) || attempt == attempts) {
                    if (isTransient(e)) recordTransientFailure();
                    throw providerFailure(e.getStatusCode().value());
                }
                lastFailure = e;
            } catch (ResourceAccessException e) {
                if (attempt == attempts) {
                    recordTransientFailure();
                    throw new AiFailure("AI_TIMEOUT", "The connection to the AI provider timed out or failed.", 504, null, 30);
                }
                lastFailure = e;
            }

            sleepBeforeRetry(attempt);
        }

        recordTransientFailure();
        throw lastFailure == null
                ? new IllegalStateException("AI provider request failed")
                : lastFailure;
    }

    private String execute(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "maxOutputTokens", 2048,
                        "temperature", 0.2,
                        "responseMimeType", "application/json",
                        "thinkingConfig", Map.of("thinkingLevel", "minimal")
                )
        );

        GeminiResponse response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AiFailure("AI_EMPTY_RESPONSE", "AI provider returned no content", 502, null, 0);
        }

        Candidate candidate = response.candidates().getFirst();
        if ("MAX_TOKENS".equals(candidate.finishReason())) {
            throw new AiFailure("AI_OUTPUT_LIMIT", "AI response exceeded the output limit. Please try again.", 502, null, 0);
        }
        if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
            throw new AiFailure("AI_EMPTY_RESPONSE", "AI provider returned an empty response", 502, null, 0);
        }

        String text = candidate.content().parts().stream()
                .map(Part::text)
                .filter(part -> part != null && !part.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow(() -> new AiFailure("AI_EMPTY_RESPONSE", "AI provider returned an empty response", 502, null, 0));

        if (text.isBlank()) {
            throw new AiFailure("AI_EMPTY_RESPONSE", "AI provider returned an empty response", 502, null, 0);
        }
        return text;
    }

    private AiFailure providerFailure(int status) {
        String code = switch (status) {
            case 400 -> "AI_REQUEST_REJECTED";
            case 401, 403 -> "AI_ACCESS_DENIED";
            case 404 -> "AI_MODEL_UNAVAILABLE";
            default -> "AI_UPSTREAM_ERROR";
        };
        return new AiFailure(code, "The AI provider rejected or failed the request (HTTP " + status + ").",
                502, status, status >= 500 ? 30 : 0);
    }

    private int retryAfter(RestClientResponseException failure) {
        String value = failure.getResponseHeaders() == null ? null : failure.getResponseHeaders().getFirst("Retry-After");
        try { return (int) Math.max(1, Math.min(86400, Long.parseLong(value))); }
        catch (Exception ignored) {
            try {
                long seconds = java.time.Duration.between(java.time.Instant.now(),
                        java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds();
                return (int) Math.max(1, Math.min(86400, seconds));
            } catch (Exception invalid) { return 60; }
        }
    }

    private boolean isTransient(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status >= 500;
    }

    private void ensureCircuitClosed() {
        long quotaRemaining = quotaUntil.get() - System.currentTimeMillis();
        if (quotaRemaining > 0) throw new AiFailure("AI_PROVIDER_QUOTA",
                "AI provider quota cooldown is active after a previous 429 response.",
                429, 429, (int) ((quotaRemaining + 999) / 1000));
        long openUntil = circuitOpenUntil.get();
        long now = System.currentTimeMillis();
        if (openUntil > now) {
            throw new AiFailure("AI_COOLDOWN", "The provider circuit is paused after repeated transient failures.", 503, null, (int) ((openUntil - now + 999) / 1000));
        }
        if (openUntil != 0 && openUntil <= now) {
            circuitOpenUntil.compareAndSet(openUntil, 0);
            consecutiveFailures.set(0);
        }
    }

    private void recordTransientFailure() {
        if (consecutiveFailures.incrementAndGet() >= circuitFailureThreshold) {
            circuitOpenUntil.set(System.currentTimeMillis() + circuitOpenDuration.toMillis());
        }
    }

    private void sleepBeforeRetry(int attempt) {
        long base = Math.max(1, retryBackoff.toMillis());
        long exponential = base * (1L << Math.min(attempt - 1, 4));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, base / 2) + 1);
        try {
            Thread.sleep(exponential + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiFailure("AI_INTERRUPTED", "AI retry was interrupted.", 503, null, 0);
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content, String finishReason) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
}
