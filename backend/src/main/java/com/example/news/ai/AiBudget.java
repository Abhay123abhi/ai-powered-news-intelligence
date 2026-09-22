package com.example.news.ai;

import com.example.news.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;

/** Counts every upstream attempt, including retries. Redis failure closes generation, not news browsing. */
@Component
public class AiBudget {
    private static final DefaultRedisScript<Long> TAKE = new DefaultRedisScript<>("""
        local minute = tonumber(redis.call('GET', KEYS[1]) or '0')
        local day = tonumber(redis.call('GET', KEYS[2]) or '0')
        if day >= tonumber(ARGV[2]) then return 2 end
        if minute >= tonumber(ARGV[1]) then return 1 end
        redis.call('INCR', KEYS[1]); redis.call('EXPIRE', KEYS[1], 120)
        redis.call('INCR', KEYS[2]); redis.call('EXPIRE', KEYS[2], ARGV[3])
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    private final int perMinute;
    private final int perDay;
    public AiBudget(StringRedisTemplate redis, @Value("${ai.requests-per-minute:5}") int perMinute,
                    @Value("${ai.requests-per-day:20}") int perDay) {
        this.redis = redis;
        this.perMinute = Math.max(1, perMinute);
        this.perDay = Math.max(1, perDay);
    }
    public void acquire() {
        Instant now = Instant.now();
        ZonedDateTime pacific = now.atZone(ZoneId.of("America/Los_Angeles"));
        long reset = Duration.between(now, pacific.toLocalDate().plusDays(1)
                .atStartOfDay(pacific.getZone()).toInstant()).toSeconds() + 1;
        Long result;
        try {
            result = redis.execute(TAKE, List.of("ai:budget:minute:" + now.getEpochSecond() / 60,
                    "ai:budget:day:" + pacific.toLocalDate()), Integer.toString(perMinute),
                    Integer.toString(perDay), Long.toString(reset + 60));
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_BUDGET_UNAVAILABLE",
                    "New AI insights are temporarily paused. Cached insights and news are still available.", 30);
        }
        if (result == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_BUDGET_UNAVAILABLE",
                "New AI insights are temporarily paused. Please try again shortly.", 30);
        if (result == 2) throw ApiException.quota(Math.toIntExact(reset));
        if (result == 1) throw ApiException.quota(60 - (int)(now.getEpochSecond() % 60));
    }
}
