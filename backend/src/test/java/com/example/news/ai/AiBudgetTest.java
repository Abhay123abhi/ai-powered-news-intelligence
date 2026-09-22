package com.example.news.ai;

import com.example.news.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class AiBudgetTest {
    @Test void rejectsMinuteAndDailyExhaustion() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L, 2L);
        var budget = new AiBudget(redis, 5, 20);
        assertThatThrownBy(budget::acquire).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(budget::acquire).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.retryAfter).isGreaterThan(0));
    }
    @Test void failsClosedWhenRedisIsDown() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> new AiBudget(redis, 5, 20).acquire()).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.code).isEqualTo("AI_BUDGET_UNAVAILABLE"));
    }
}
