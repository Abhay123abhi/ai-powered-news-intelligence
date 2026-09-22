package com.example.news.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final int retryAfter;

    public ApiException(HttpStatus status, String code, String message, int retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }
    public static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "FEED_EXPIRED", "This feed has expired. Refresh the news to continue.", 0);
    }
    public static ApiException quota(int seconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_QUOTA_REACHED",
                "The shared AI allowance is resting. You can still browse the news and read cached insights.", seconds);
    }
}
