package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlRequest;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlResponse;
import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAuditStatus;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.common.exception.ConflictException;
import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AgentUploadUrlService {

    private static final Logger log = LoggerFactory.getLogger(AgentUploadUrlService.class);
    private static final long DEFAULT_MAX_XML_SIZE_BYTES = 20L * 1024 * 1024;

    private final StorageService storageService;
    private final AgentUploadAuditService auditService;
    private final int expiresSeconds;
    private final long maxXmlSizeBytes;

    public AgentUploadUrlService(StorageService storageService,
                                 AgentUploadAuditService auditService,
                                 @Value("${app.agent.upload-url-expires-seconds:900}") int expiresSeconds,
                                 @Value("${app.agent.max-xml-size-bytes:" + DEFAULT_MAX_XML_SIZE_BYTES + "}") long maxXmlSizeBytes) {
        this.storageService = storageService;
        this.auditService = auditService;
        this.expiresSeconds = expiresSeconds;
        this.maxXmlSizeBytes = maxXmlSizeBytes;
    }

    @Transactional(readOnly = true)
    public AgentUploadUrlResponse generateUploadUrl(AgentAuthContext auth, AgentUploadUrlRequest request) {
        String sha = request == null || request.sha256() == null ? null : request.sha256().trim().toLowerCase(Locale.ROOT);
        String objectKey = null;

        if (auth == null) {
            throw new ValidationException("Contexto de autenticacao do agente ausente");
        }

        try {
            validateRequest(request, sha);

            objectKey = "%d/%d/%s.xml".formatted(auth.tenantId(), auth.empresaId(), sha);

            boolean exists = storageService.exists(objectKey);
            if (exists) {
                auditService.record(auth, objectKey, sha, AgentUploadAuditStatus.CONFLICT, "Objeto ja existe");
                throw new ConflictException("Objeto ja existe para sha256 informado");
            }

            String uploadUrl = storageService.generatePresignedPutUrl(objectKey, expiresSeconds, "application/xml");
            auditService.record(auth, objectKey, sha, AgentUploadAuditStatus.SUCCESS, "URL assinada emitida");

            log.info("agent.upload_url.generated tenantId={} empresaId={} keyPrefix={} objectKey={} expiresIn={} sizeBytes={}",
                    auth.tenantId(), auth.empresaId(), auth.keyPrefix(), objectKey, expiresSeconds, request.sizeBytes());
            return new AgentUploadUrlResponse(uploadUrl, objectKey, expiresSeconds);
        } catch (ValidationException e) {
            auditService.record(auth, objectKey, sha, AgentUploadAuditStatus.VALIDATION_ERROR, e.getMessage());
            throw e;
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            auditService.record(auth, objectKey, sha, AgentUploadAuditStatus.INFRA_ERROR, e.getMessage());
            throw new InfraException("Falha ao gerar URL de upload", e);
        }
    }

    private void validateRequest(AgentUploadUrlRequest request, String sha) {
        if (request == null) {
            throw new ValidationException("Request obrigatorio");
        }
        if (sha == null || !sha.matches("^[a-f0-9]{64}$")) {
            throw new ValidationException("sha256 invalido");
        }
        if (request.sizeBytes() != null && request.sizeBytes() > maxXmlSizeBytes) {
            throw new ValidationException("sizeBytes excede limite maximo permitido");
        }
        if (request.originalFileName() != null && !request.originalFileName().isBlank()
                && !request.originalFileName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new ValidationException("originalFileName deve ter extensao .xml");
        }
    }
}
