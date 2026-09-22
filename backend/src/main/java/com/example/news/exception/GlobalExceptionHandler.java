package com.example.news.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.status, ex.getMessage());
        body.setProperty("code", ex.code);
        body.setProperty("retryAfter", ex.retryAfter);
        return ResponseEntity.status(ex.status)
                .headers(headers -> { if (ex.retryAfter > 0) headers.set("Retry-After", Integer.toString(ex.retryAfter)); })
                .body(body);
    }
    @ExceptionHandler(NewsUnavailableException.class)
    ProblemDetail handleNews(NewsUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInput(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleInvalidAi(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "AI could not produce a usable insight. Please try another selection or return later.");
    }
}
