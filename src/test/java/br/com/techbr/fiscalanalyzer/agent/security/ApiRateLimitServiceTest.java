package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiRateLimitServiceTest {

    @Test
    void enforceAuthenticated_excedeLimite_retorna429() {
        ApiRateLimitService service = new ApiRateLimitService(
                true,
                1d, 1,     // session: burst 1; segunda chamada imediata deve bloquear
                20d, 200,
                5d, 20,
                10d, 50,
                2d, 10,
                20, 300, 600,
                new SimpleMeterRegistry()
        );

        assertDoesNotThrow(() -> service.enforceAuthenticated("agent.session", 1L, "fp-a"));
        assertThrows(TooManyRequestsException.class,
                () -> service.enforceAuthenticated("agent.session", 1L, "fp-a"));
    }

    @Test
    void registerAuthFailure_atingeThreshold_ativaLockout() {
        ApiRateLimitService service = new ApiRateLimitService(
                true,
                1d, 5,
                20d, 200,
                5d, 20,
                10d, 50,
                100d, 100, // anon alto para não interferir
                2, 300, 60,
                new SimpleMeterRegistry()
        );

        assertDoesNotThrow(() -> service.registerAuthFailure("agent.session", "fp-b", "invalid-key"));
        assertThrows(TooManyRequestsException.class,
                () -> service.registerAuthFailure("agent.session", "fp-b", "invalid-key"));
        assertThrows(TooManyRequestsException.class,
                () -> service.registerAuthFailure("agent.session", "fp-b", "invalid-key"));
    }

    @Test
    void registerAuthSuccess_limpaEstadoDeFalha() {
        ApiRateLimitService service = new ApiRateLimitService(
                true,
                1d, 5,
                20d, 200,
                5d, 20,
                10d, 50,
                100d, 100,
                3, 300, 60,
                new SimpleMeterRegistry()
        );

        assertDoesNotThrow(() -> service.registerAuthFailure("agent.session", "fp-c", "invalid-key"));
        assertDoesNotThrow(() -> service.registerAuthFailure("agent.session", "fp-c", "invalid-key"));
        service.registerAuthSuccess("fp-c");
        assertDoesNotThrow(() -> service.registerAuthFailure("agent.session", "fp-c", "invalid-key"));
    }
}
