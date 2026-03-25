package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAudit;
import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAuditStatus;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentUploadAuditRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentUploadAuditServiceTest {

    @Mock
    private AgentUploadAuditRepository repository;

    @Test
    void record_salvaAuditoria() {
        AgentUploadAuditService service = new AgentUploadAuditService(repository);
        AgentAuthContext auth = new AgentAuthContext(10L, 99L, 42L, "fa_live_ab");

        service.record(auth, "99/42/a.xml", "abcd", AgentUploadAuditStatus.SUCCESS, "URL assinada emitida");

        ArgumentCaptor<AgentUploadAudit> captor = ArgumentCaptor.forClass(AgentUploadAudit.class);
        verify(repository).save(captor.capture());
        AgentUploadAudit saved = captor.getValue();
        assertEquals(99L, saved.getTenantId());
        assertEquals(42L, saved.getEmpresaId());
        assertEquals(10L, saved.getApiKeyId());
        assertEquals("fa_live_ab", saved.getKeyPrefix());
        assertEquals("/agent/upload-url", saved.getEndpoint());
        assertEquals("99/42/a.xml", saved.getObjectKey());
        assertEquals("abcd", saved.getSha256());
        assertEquals(AgentUploadAuditStatus.SUCCESS, saved.getStatus());
        assertEquals("URL assinada emitida", saved.getDetail());
    }

    @Test
    void record_erroPersistencia_naoPropaga() {
        AgentUploadAuditService service = new AgentUploadAuditService(repository);
        AgentAuthContext auth = new AgentAuthContext(10L, 99L, 42L, "fa_live_ab");
        when(repository.save(any(AgentUploadAudit.class))).thenThrow(new RuntimeException("db down"));

        service.record(auth, "99/42/a.xml", "abcd", AgentUploadAuditStatus.INFRA_ERROR, "Falha de storage");

        verify(repository).save(any(AgentUploadAudit.class));
    }

    @Test
    void record_semAuth_naoSalva() {
        AgentUploadAuditService service = new AgentUploadAuditService(repository);

        service.record(null, "99/42/a.xml", "abcd", AgentUploadAuditStatus.SUCCESS, "OK");

        verify(repository, never()).save(any());
    }
}
