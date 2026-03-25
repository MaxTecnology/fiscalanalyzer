package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AdminCreateAgentKeyRequest;
import br.com.techbr.fiscalanalyzer.agent.model.AgentApiKey;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentApiKeyRepository;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApiKeyServiceConcurrencyTest {

    @Mock
    private AgentApiKeyRepository repository;

    @Test
    void rotateKey_concorrente_apenasUmaRotacaoComRevogacao() throws Exception {
        AgentApiKeyService service = new AgentApiKeyService(repository);

        CountDownLatch findBarrier = new CountDownLatch(2);
        AtomicLong keyIdSequence = new AtomicLong(200L);
        AtomicBoolean currentActive = new AtomicBoolean(true);
        AtomicInteger revokeCalls = new AtomicInteger(0);

        when(repository.findById(77L)).thenAnswer(invocation -> {
            findBarrier.countDown();
            findBarrier.await(2, TimeUnit.SECONDS);
            AgentApiKey key = new AgentApiKey();
            ReflectionTestUtils.setField(key, "id", 77L);
            key.setTenantId(1L);
            key.setEmpresaId(2L);
            key.setAtivo(true);
            return Optional.of(key);
        });

        when(repository.save(any(AgentApiKey.class))).thenAnswer(invocation -> {
            AgentApiKey entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", keyIdSequence.getAndIncrement());
            }
            return entity;
        });

        when(repository.revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class))).thenAnswer(invocation -> {
            revokeCalls.incrementAndGet();
            return currentActive.compareAndSet(true, false) ? 1 : 0;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> task = () -> {
            try {
                service.rotateKey(1L, 2L, 77L, true, new AdminCreateAgentKeyRequest("rot", "admin"));
                return true;
            } catch (ValidationException ex) {
                return false;
            }
        };

        Future<Boolean> r1 = pool.submit(task);
        Future<Boolean> r2 = pool.submit(task);

        boolean s1 = r1.get(5, TimeUnit.SECONDS);
        boolean s2 = r2.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();

        int success = (s1 ? 1 : 0) + (s2 ? 1 : 0);
        int failure = (s1 ? 0 : 1) + (s2 ? 0 : 1);

        assertEquals(1, success);
        assertEquals(1, failure);
        assertEquals(2, revokeCalls.get());
    }

    @Test
    void revokeKey_concorrente_apenasUmaRevogacao() throws Exception {
        AgentApiKeyService service = new AgentApiKeyService(repository);

        CountDownLatch findBarrier = new CountDownLatch(2);
        AtomicBoolean currentActive = new AtomicBoolean(true);

        when(repository.findById(77L)).thenAnswer(invocation -> {
            findBarrier.countDown();
            findBarrier.await(2, TimeUnit.SECONDS);
            AgentApiKey key = new AgentApiKey();
            ReflectionTestUtils.setField(key, "id", 77L);
            key.setTenantId(1L);
            key.setEmpresaId(2L);
            key.setAtivo(true);
            return Optional.of(key);
        });
        when(repository.revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class))).thenAnswer(invocation ->
                currentActive.compareAndSet(true, false) ? 1 : 0
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> task = () -> {
            try {
                service.revokeKey(1L, 2L, 77L);
                return true;
            } catch (ValidationException ex) {
                return false;
            }
        };

        Future<Boolean> r1 = pool.submit(task);
        Future<Boolean> r2 = pool.submit(task);

        boolean s1 = r1.get(5, TimeUnit.SECONDS);
        boolean s2 = r2.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();

        int success = (s1 ? 1 : 0) + (s2 ? 1 : 0);
        int failure = (s1 ? 0 : 1) + (s2 ? 0 : 1);

        assertEquals(1, success);
        assertEquals(1, failure);
    }
}
