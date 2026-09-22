package com.example.news.ai;

/** Safe diagnostic metadata, never raw provider bodies, prompts, or credentials. */
public class AiFailure extends RuntimeException {
    private final String code;
    private final int status;
    private final Integer upstreamStatus;
    private final int retryAfter;

    public AiFailure(String code, String reason, int status, Integer upstreamStatus, int retryAfter) {
        super(reason);
        this.code = code;
        this.status = status;
        this.upstreamStatus = upstreamStatus;
        this.retryAfter = Math.max(0, retryAfter);
    }
    public String code() { return code; }
    public int status() { return status; }
    public Integer upstreamStatus() { return upstreamStatus; }
    public int retryAfter() { return retryAfter; }
}
