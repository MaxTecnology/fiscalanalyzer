package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.repository.AgentAuthAuditRepository;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentUploadAuditRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAuditCleanupServiceTest {

    @Mock
    private AgentAuthAuditRepository authAuditRepository;

    @Mock
    private AgentUploadAuditRepository uploadAuditRepository;

    @Test
    void cleanup_disabled_naoExecutaDelete() {
        AgentAuditCleanupService service = new AgentAuditCleanupService(
                authAuditRepository,
                uploadAuditRepository,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC),
                false,
                90,
                30,
                5000,
                100
        );

        AgentAuditCleanupService.CleanupResult result = service.cleanup("test");

        assertTrue(result.skipped());
        assertEquals(0, result.authDeleted());
        assertEquals(0, result.uploadDeleted());
        verify(authAuditRepository, never()).deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(uploadAuditRepository, never()).deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void cleanup_emLotesIncrementaMetricas() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC);

        when(authAuditRepository.deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(5000)))
                .thenReturn(5000)
                .thenReturn(1200);
        when(uploadAuditRepository.deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(5000)))
                .thenReturn(400);

        AgentAuditCleanupService service = new AgentAuditCleanupService(
                authAuditRepository,
                uploadAuditRepository,
                meterRegistry,
                clock,
                true,
                90,
                30,
                5000,
                100
        );

        AgentAuditCleanupService.CleanupResult result = service.cleanup("manual");

        assertEquals(6200, result.authDeleted());
        assertEquals(400, result.uploadDeleted());
        assertTrue(!result.skipped());

        double authCounter = meterRegistry.counter("security.audit.cleanup.deleted", "table", "agent_auth_audit").count();
        double uploadCounter = meterRegistry.counter("security.audit.cleanup.deleted", "table", "agent_upload_audit").count();
        assertEquals(6200.0, authCounter);
        assertEquals(400.0, uploadCounter);

        ArgumentCaptor<Instant> authCutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(authAuditRepository, times(2)).deleteBatchBefore(authCutoffCaptor.capture(), org.mockito.ArgumentMatchers.eq(5000));
        assertEquals(Instant.parse("2025-12-24T10:00:00Z"), authCutoffCaptor.getAllValues().getFirst());

        ArgumentCaptor<Instant> uploadCutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(uploadAuditRepository, times(1)).deleteBatchBefore(uploadCutoffCaptor.capture(), org.mockito.ArgumentMatchers.eq(5000));
        assertEquals(Instant.parse("2026-02-22T10:00:00Z"), uploadCutoffCaptor.getValue());
    }

    @Test
    void cleanup_respeitaLimiteDeBatches() {
        when(authAuditRepository.deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(100)
                .thenReturn(100)
                .thenReturn(100);
        when(uploadAuditRepository.deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(0);

        AgentAuditCleanupService service = new AgentAuditCleanupService(
                authAuditRepository,
                uploadAuditRepository,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-03-24T10:00:00Z"), ZoneOffset.UTC),
                true,
                90,
                30,
                100,
                2
        );

        AgentAuditCleanupService.CleanupResult result = service.cleanup("test");

        assertEquals(200, result.authDeleted());
        verify(authAuditRepository, times(2)).deleteBatchBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100));
    }
}
