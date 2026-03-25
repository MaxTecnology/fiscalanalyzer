package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAudit;
import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAuditStatus;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentUploadAuditRepository;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentUploadAuditService {

    private static final Logger log = LoggerFactory.getLogger(AgentUploadAuditService.class);
    private static final String ENDPOINT_UPLOAD_URL = "/agent/upload-url";
    private static final int DETAIL_LIMIT = 500;

    private final AgentUploadAuditRepository repository;

    public AgentUploadAuditService(AgentUploadAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AgentAuthContext auth,
                       String objectKey,
                       String sha256,
                       AgentUploadAuditStatus status,
                       String detail) {
        if (auth == null || auth.tenantId() == null || auth.empresaId() == null || status == null) {
            return;
        }

        try {
            AgentUploadAudit entry = new AgentUploadAudit();
            entry.setTenantId(auth.tenantId());
            entry.setEmpresaId(auth.empresaId());
            entry.setApiKeyId(auth.apiKeyId());
            entry.setKeyPrefix(auth.keyPrefix());
            entry.setEndpoint(ENDPOINT_UPLOAD_URL);
            entry.setObjectKey(objectKey);
            entry.setSha256(sha256);
            entry.setStatus(status);
            entry.setDetail(truncate(detail));
            repository.save(entry);
        } catch (Exception e) {
            log.warn("agent.upload_url.audit_error tenantId={} empresaId={} keyPrefix={} status={} message={}",
                    auth.tenantId(), auth.empresaId(), auth.keyPrefix(), status.name(), e.getMessage());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= DETAIL_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, DETAIL_LIMIT);
    }
}
