package com.example.news.exception;

import com.example.news.ai.AiFailure;
import com.example.news.controller.AiController;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.UUID;

@Order(-1)
@RestControllerAdvice(assignableTypes = AiController.class)
public class AiExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiExceptionHandler.class);

    @ExceptionHandler(AiFailure.class)
    public ResponseEntity<ProblemDetail> aiFailure(AiFailure failure) {
        String requestId = UUID.randomUUID().toString();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(failure.status()), failure.getMessage());
        body.setTitle("AI request could not be completed");
        body.setProperty("code", failure.code());
        body.setProperty("requestId", requestId);
        if (failure.upstreamStatus() != null) body.setProperty("upstreamStatus", failure.upstreamStatus());
        if (failure.retryAfter() > 0) body.setProperty("retryAfter", failure.retryAfter());
        log.warn("AI failure requestId={} code={} upstreamStatus={}", requestId, failure.code(), failure.upstreamStatus());
        var response = ResponseEntity.status(failure.status());
        if (failure.retryAfter() > 0) response.header("Retry-After", Integer.toString(failure.retryAfter()));
        return response.header("Cache-Control", "no-store").body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> invalidRequest(Exception ignored) {
        return aiFailure(new AiFailure("AI_INVALID_REQUEST", "The AI request is missing valid articles or a valid question.", 400, null, 0));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ignored) {
        // Do not expose arbitrary exception messages: they may contain provider payloads or credentials.
        return aiFailure(new AiFailure("AI_INTERNAL_ERROR", "An unexpected internal AI processing failure occurred.", 500, null, 0));
    }
}
