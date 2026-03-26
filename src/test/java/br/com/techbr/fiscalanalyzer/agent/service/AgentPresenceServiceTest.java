package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentHeartbeatRequest;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatus;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentInstanceStatusRepository;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPresenceServiceTest {

    @Mock
    private AgentInstanceStatusRepository repository;
    @Mock
    private TenantEmpresaValidationService tenantEmpresaValidationService;

    private AgentPresenceService service;

    @BeforeEach
    void setUp() {
        service = new AgentPresenceService(repository, tenantEmpresaValidationService, 30, 90, 300);
    }

    @Test
    void registerSession_semAgentId_usaFallbackPorApiKey() {
        var context = new AgentAuthContext(77L, 99L, 42L, "fa_live_ab");
        when(repository.findByTenantIdAndEmpresaIdAndAgentId(99L, 42L, "api-key-77"))
                .thenReturn(Optional.empty());
        when(repository.save(any(AgentInstanceStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.registerSession(context, null, "hash-1");

        assertEquals("api-key-77", response.agentId());
        assertEquals(AgentInstanceStatusType.ONLINE, response.status());
        assertEquals(30, response.heartbeatIntervalSeconds());
    }

    @Test
    void heartbeat_comErroMarcaDegraded() {
        var context = new AgentAuthContext(77L, 99L, 42L, "fa_live_ab");
        AgentInstanceStatus entity = new AgentInstanceStatus();
        entity.setTenantId(99L);
        entity.setEmpresaId(42L);
        entity.setAgentId("desktop-agent-01");
        entity.setStatus(AgentInstanceStatusType.ONLINE);
        entity.setLastSeenAt(Instant.now());

        when(repository.findByTenantIdAndEmpresaIdAndAgentId(99L, 42L, "desktop-agent-01"))
                .thenReturn(Optional.of(entity));
        when(repository.save(any(AgentInstanceStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.heartbeat(
                context,
                "desktop-agent-01",
                "hash-1",
                new AgentHeartbeatRequest("1.2.3", "host-a", 2, 10, 3, "UPLOAD_TIMEOUT", "timeout", null, null)
        );

        assertEquals(AgentInstanceStatusType.DEGRADED, response.status());
        assertEquals("1.2.3", entity.getAgentVersion());
        assertEquals("UPLOAD_TIMEOUT", entity.getLastErrorCode());
        assertEquals(3, entity.getErrorCountWindow());
    }

    @Test
    void list_calculaOfflinePeloLastSeenMesmoSemScheduler() {
        AgentInstanceStatus entity = new AgentInstanceStatus();
        entity.setAgentId("desktop-agent-01");
        entity.setTenantId(99L);
        entity.setEmpresaId(42L);
        entity.setStatus(AgentInstanceStatusType.ONLINE);
        entity.setLastSeenAt(Instant.now().minusSeconds(600));

        when(repository.findByTenantIdAndEmpresaIdOrderByLastSeenAtDesc(99L, 42L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        var list = service.list(99L, 42L, null, PageRequest.of(0, 20));

        assertEquals(1, list.getTotalElements());
        assertEquals(AgentInstanceStatusType.OFFLINE, list.getContent().getFirst().status());
    }

    @Test
    void summary_retornaContadores() {
        AgentInstanceStatus online = new AgentInstanceStatus();
        online.setStatus(AgentInstanceStatusType.ONLINE);
        online.setLastSeenAt(Instant.now());

        AgentInstanceStatus revoked = new AgentInstanceStatus();
        revoked.setStatus(AgentInstanceStatusType.REVOKED);
        revoked.setLastSeenAt(Instant.now());

        when(repository.findByTenantIdAndEmpresaIdOrderByLastSeenAtDesc(99L, 42L))
                .thenReturn(List.of(online, revoked));

        var summary = service.summary(99L, 42L);

        assertEquals(2, summary.total());
        assertEquals(1, summary.online());
        assertEquals(1, summary.revoked());
    }

    @Test
    void refreshStaleStatuses_executaAtualizacoesPorJanela() {
        when(repository.markStatusByLastSeenBefore(eq(AgentInstanceStatusType.OFFLINE), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(2);
        when(repository.markStatusByLastSeenBefore(eq(AgentInstanceStatusType.DEGRADED), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(3);

        service.refreshStaleStatuses();

        verify(repository).markStatusByLastSeenBefore(eq(AgentInstanceStatusType.OFFLINE), anyCollection(), any(Instant.class), any(Instant.class));
        verify(repository).markStatusByLastSeenBefore(eq(AgentInstanceStatusType.DEGRADED), anyCollection(), any(Instant.class), any(Instant.class));
    }
}
