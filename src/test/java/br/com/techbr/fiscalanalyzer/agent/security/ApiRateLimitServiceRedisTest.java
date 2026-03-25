package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiRateLimitServiceRedisTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void enforceAuthenticated_redisBloqueia_comRetryAfter() {
        ApiRateLimitService service = new ApiRateLimitService(
                true,
                1d, 5,
                20d, 200,
                5d, 20,
                10d, 50,
                2d, 10,
                20, 300, 600,
                "redis",
                "fa:test:",
                600,
                redisTemplate,
                new SimpleMeterRegistry()
        );

        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, 1500L));

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
                () -> service.enforceAuthenticated("agent.session", 10L, "fp-1"));
        assertEquals(2L, ex.getRetryAfterSeconds());
    }

    @Test
    void enforceAuthenticated_redisErro_fallbackInMemory() {
        ApiRateLimitService service = new ApiRateLimitService(
                true,
                1d, 1,
                20d, 200,
                5d, 20,
                10d, 50,
                2d, 10,
                20, 300, 600,
                "redis",
                "fa:test:",
                600,
                redisTemplate,
                new SimpleMeterRegistry()
        );

        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("redis timeout"));

        assertDoesNotThrow(() -> service.enforceAuthenticated("agent.session", 11L, "fp-2"));
    }

    @Test
    void registerAuthFailure_redisAtingeThreshold_ativaLockout() {
        ApiRateLimitService service = new ApiRateLimitService(
                true,
                1d, 5,
                20d, 200,
                5d, 20,
                10d, 50,
                2d, 10,
                2, 300, 60,
                "redis",
                "fa:test:",
                600,
                redisTemplate,
                new SimpleMeterRegistry()
        );

        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 0L));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fa:test:auth:lock:fp-3")).thenReturn(null);
        when(valueOperations.increment("fa:test:auth:fail:fp-3")).thenReturn(2L);

        assertThrows(TooManyRequestsException.class,
                () -> service.registerAuthFailure("agent.session", "fp-3", "invalid"));

        verify(valueOperations).set(eq("fa:test:auth:lock:fp-3"), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete("fa:test:auth:fail:fp-3");
    }
}
