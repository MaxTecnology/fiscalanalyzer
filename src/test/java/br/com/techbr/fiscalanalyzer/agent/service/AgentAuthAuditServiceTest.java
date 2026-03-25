package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAudit;
import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAuditScope;
import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAuditResult;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentAuthAuditRepository;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentAuthAuditServiceTest {

    @Mock
    private AgentAuthAuditRepository repository;

    @Test
    void modeFailures_naoPersisteSucesso() {
        AgentAuthAuditService service = new AgentAuthAuditService(repository, "failures");
        AgentAuthContext context = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");

        service.recordAgentSuccess("agent.session", context, "fp");

        verify(repository, never()).save(any());
    }

    @Test
    void modeFailures_persisteFalha() {
        AgentAuthAuditService service = new AgentAuthAuditService(repository, "failures");

        service.recordAgentFailure("agent.session", null, "fp", AgentAuthAuditResult.UNAUTHORIZED, "Authorization Bearer ausente");

        verify(repository).save(any(AgentAuthAudit.class));
    }

    @Test
    void modeOff_naoPersisteNada() {
        AgentAuthAuditService service = new AgentAuthAuditService(repository, "off");

        service.recordAgentFailure("agent.session", null, "fp", AgentAuthAuditResult.UNAUTHORIZED, "erro");

        verify(repository, never()).save(any());
    }

    @Test
    void modeAll_persisteSucessoComContexto() {
        AgentAuthAuditService service = new AgentAuthAuditService(repository, "all");
        AgentAuthContext context = new AgentAuthContext(10L, 99L, 42L, "fa_live_ab");

        service.recordAgentSuccess("agent.upload-url", context, "fp");

        ArgumentCaptor<AgentAuthAudit> captor = ArgumentCaptor.forClass(AgentAuthAudit.class);
        verify(repository).save(captor.capture());
        AgentAuthAudit saved = captor.getValue();
        assertEquals("agent.upload-url", saved.getRoute());
        assertEquals(AgentAuthAuditScope.AGENT, saved.getAuthScope());
        assertEquals(99L, saved.getTenantId());
        assertEquals(42L, saved.getEmpresaId());
        assertEquals(10L, saved.getApiKeyId());
        assertEquals("fa_live_ab", saved.getKeyPrefix());
        assertEquals(AgentAuthAuditResult.SUCCESS, saved.getResult());
    }
}
