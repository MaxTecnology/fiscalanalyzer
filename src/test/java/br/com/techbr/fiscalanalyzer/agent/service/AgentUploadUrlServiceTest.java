package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlRequest;
import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAuditStatus;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.common.exception.ConflictException;
import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AgentUploadUrlServiceTest {

    @Mock
    private StorageService storageService;
    @Mock
    private AgentUploadAuditService auditService;

    @Test
    void generateUploadUrl_sucesso() {
        AgentUploadUrlService service = new AgentUploadUrlService(storageService, auditService, 900, 20L * 1024 * 1024);
        AgentAuthContext auth = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        AgentUploadUrlRequest request = new AgentUploadUrlRequest(
                "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                "nfe-001.xml",
                1500L
        );
        String key = "99/42/9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869.xml";

        when(storageService.exists(key)).thenReturn(false);
        when(storageService.generatePresignedPutUrl(eq(key), eq(900), eq("application/xml")))
                .thenReturn("https://upload.example");

        var response = service.generateUploadUrl(auth, request);

        assertEquals(key, response.objectKey());
        assertEquals("https://upload.example", response.uploadUrl());
        assertEquals(900, response.expiresIn());
        verify(auditService).record(auth, key, request.sha256(), AgentUploadAuditStatus.SUCCESS, "URL assinada emitida");
    }

    @Test
    void generateUploadUrl_duplicado_retorna409() {
        AgentUploadUrlService service = new AgentUploadUrlService(storageService, auditService, 900, 20L * 1024 * 1024);
        AgentAuthContext auth = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        AgentUploadUrlRequest request = new AgentUploadUrlRequest(
                "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                "nfe-001.xml",
                1500L
        );
        String key = "99/42/9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869.xml";
        when(storageService.exists(key)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.generateUploadUrl(auth, request));
        verify(storageService).exists(key);
        verify(auditService).record(auth, key, request.sha256(), AgentUploadAuditStatus.CONFLICT, "Objeto ja existe");
    }

    @Test
    void generateUploadUrl_tamanhoExcedido_retorna400() {
        AgentUploadUrlService service = new AgentUploadUrlService(storageService, auditService, 900, 100L);
        AgentAuthContext auth = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        AgentUploadUrlRequest request = new AgentUploadUrlRequest(
                "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                "nfe-001.xml",
                101L
        );

        assertThrows(ValidationException.class, () -> service.generateUploadUrl(auth, request));
        verify(auditService).record(auth, null, request.sha256(), AgentUploadAuditStatus.VALIDATION_ERROR, "sizeBytes excede limite maximo permitido");
        verify(storageService, never()).exists(any());
    }

    @Test
    void generateUploadUrl_nomeArquivoInvalido_retorna400() {
        AgentUploadUrlService service = new AgentUploadUrlService(storageService, auditService, 900, 20L * 1024 * 1024);
        AgentAuthContext auth = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        AgentUploadUrlRequest request = new AgentUploadUrlRequest(
                "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                "nfe-001.txt",
                10L
        );

        assertThrows(ValidationException.class, () -> service.generateUploadUrl(auth, request));
        verify(auditService).record(auth, null, request.sha256(), AgentUploadAuditStatus.VALIDATION_ERROR, "originalFileName deve ter extensao .xml");
    }

    @Test
    void generateUploadUrl_falhaInfra_registraAuditoria() {
        AgentUploadUrlService service = new AgentUploadUrlService(storageService, auditService, 900, 20L * 1024 * 1024);
        AgentAuthContext auth = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        AgentUploadUrlRequest request = new AgentUploadUrlRequest(
                "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                "nfe-001.xml",
                10L
        );
        String key = "99/42/9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869.xml";
        when(storageService.exists(key)).thenThrow(new RuntimeException("timeout"));

        assertThrows(InfraException.class, () -> service.generateUploadUrl(auth, request));
        verify(auditService).record(auth, key, request.sha256(), AgentUploadAuditStatus.INFRA_ERROR, "timeout");
    }
}
