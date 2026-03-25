package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.model.*;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentAuthAuditRepository;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class AgentAuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AgentAuthAuditService.class);
    private static final int DETAIL_LIMIT = 500;

    private final AgentAuthAuditRepository repository;
    private final Mode mode;

    public AgentAuthAuditService(AgentAuthAuditRepository repository,
                                 @Value("${app.security.auth-audit.mode:failures}") String mode) {
        this.repository = repository;
        this.mode = Mode.from(mode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAgentSuccess(String route, AgentAuthContext context, String fingerprint) {
        if (mode != Mode.ALL || context == null) {
            return;
        }
        persist(AgentAuthAuditScope.AGENT, route, context, fingerprint, AgentAuthAuditResult.SUCCESS, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAgentFailure(String route,
                                   AgentAuthContext context,
                                   String fingerprint,
                                   AgentAuthAuditResult result,
                                   String detail) {
        if (mode == Mode.OFF) {
            return;
        }
        persist(AgentAuthAuditScope.AGENT, route, context, fingerprint, result, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAdminFailure(String route, String fingerprint, AgentAuthAuditResult result, String detail) {
        if (mode == Mode.OFF) {
            return;
        }
        persist(AgentAuthAuditScope.ADMIN, route, null, fingerprint, result, detail);
    }

    private void persist(AgentAuthAuditScope scope,
                         String route,
                         AgentAuthContext context,
                         String fingerprint,
                         AgentAuthAuditResult result,
                         String detail) {
        if (result == null || !StringUtils.hasText(route)) {
            return;
        }
        try {
            AgentAuthAudit entry = new AgentAuthAudit();
            entry.setAuthScope(scope);
            entry.setRoute(route);
            if (context != null) {
                entry.setTenantId(context.tenantId());
                entry.setEmpresaId(context.empresaId());
                entry.setApiKeyId(context.apiKeyId());
                entry.setKeyPrefix(context.keyPrefix());
            }
            entry.setFingerprint(truncate(fingerprint, 64));
            entry.setResult(result);
            entry.setDetail(truncate(detail, DETAIL_LIMIT));
            repository.save(entry);
        } catch (Exception e) {
            log.warn("security.auth.audit_error route={} scope={} result={} message={}",
                    route, scope, result, e.getMessage());
        }
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }

    enum Mode {
        OFF,
        FAILURES,
        ALL;

        static Mode from(String raw) {
            if (!StringUtils.hasText(raw)) {
                return FAILURES;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "off" -> OFF;
                case "all" -> ALL;
                default -> FAILURES;
            };
        }
    }
}
