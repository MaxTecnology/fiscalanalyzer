package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentHeartbeatRequest;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentHeartbeatResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentInstanceStatusResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentInstanceStatusSummaryResponse;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatus;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentInstanceStatusRepository;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentPresenceService {

    private static final Logger log = LoggerFactory.getLogger(AgentPresenceService.class);
    private static final Set<AgentInstanceStatusType> OFFLINE_ELIGIBLE = EnumSet.of(
            AgentInstanceStatusType.ONLINE,
            AgentInstanceStatusType.DEGRADED
    );

    private final AgentInstanceStatusRepository repository;
    private final TenantEmpresaValidationService tenantEmpresaValidationService;
    private final int heartbeatIntervalSeconds;
    private final int degradedAfterSeconds;
    private final int offlineAfterSeconds;

    public AgentPresenceService(
            AgentInstanceStatusRepository repository,
            TenantEmpresaValidationService tenantEmpresaValidationService,
            @Value("${app.agent.presence.heartbeat-interval-seconds:30}") int heartbeatIntervalSeconds,
            @Value("${app.agent.presence.degraded-after-seconds:90}") int degradedAfterSeconds,
            @Value("${app.agent.presence.offline-after-seconds:300}") int offlineAfterSeconds
    ) {
        this.repository = repository;
        this.tenantEmpresaValidationService = tenantEmpresaValidationService;
        this.heartbeatIntervalSeconds = Math.max(5, heartbeatIntervalSeconds);
        this.degradedAfterSeconds = Math.max(30, degradedAfterSeconds);
        this.offlineAfterSeconds = Math.max(this.degradedAfterSeconds + 1, offlineAfterSeconds);
    }

    @Transactional
    public AgentHeartbeatResponse registerSession(AgentAuthContext context, String agentId, String ipHash) {
        return upsert(context, agentId, ipHash, null);
    }

    @Transactional
    public AgentHeartbeatResponse heartbeat(AgentAuthContext context, String agentId, String ipHash, AgentHeartbeatRequest request) {
        return upsert(context, agentId, ipHash, request);
    }

    @Transactional(readOnly = true)
    public Page<AgentInstanceStatusResponse> list(Long tenantId,
                                                  Long empresaId,
                                                  AgentInstanceStatusType statusFilter,
                                                  Pageable pageable) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        Instant now = Instant.now();
        Page<AgentInstanceStatus> page = statusFilter == null
                ? repository.findByTenantIdAndEmpresaIdOrderByLastSeenAtDesc(tenantId, empresaId, pageable)
                : repository.findByTenantIdAndEmpresaIdAndStatusOrderByLastSeenAtDesc(tenantId, empresaId, statusFilter, pageable);
        return page.map(entity -> toResponse(entity, now));
    }

    @Transactional(readOnly = true)
    public AgentInstanceStatusResponse detail(Long tenantId, Long empresaId, String agentId) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        String normalizedAgentId = normalizeAgentId(agentId, null);
        AgentInstanceStatus entity = repository.findByTenantIdAndEmpresaIdAndAgentId(tenantId, empresaId, normalizedAgentId)
                .orElseThrow(() -> new ValidationException("Agent nao encontrado: " + normalizedAgentId));
        return toResponse(entity, Instant.now());
    }

    @Transactional(readOnly = true)
    public AgentInstanceStatusSummaryResponse summary(Long tenantId, Long empresaId) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        Instant now = Instant.now();

        long online = 0;
        long degraded = 0;
        long offline = 0;
        long blocked = 0;
        long revoked = 0;

        List<AgentInstanceStatus> statuses = repository.findByTenantIdAndEmpresaIdOrderByLastSeenAtDesc(tenantId, empresaId);
        for (AgentInstanceStatus status : statuses) {
            AgentInstanceStatusType effective = resolveStatus(status, now);
            switch (effective) {
                case ONLINE -> online++;
                case DEGRADED -> degraded++;
                case OFFLINE -> offline++;
                case BLOCKED -> blocked++;
                case REVOKED -> revoked++;
            }
        }

        return new AgentInstanceStatusSummaryResponse(
                tenantId,
                empresaId,
                statuses.size(),
                online,
                degraded,
                offline,
                blocked,
                revoked,
                now
        );
    }

    @Scheduled(fixedDelayString = "${app.agent.presence.status-refresh-interval-ms:30000}")
    @Transactional
    public void refreshStaleStatuses() {
        Instant now = Instant.now();
        Instant offlineCutoff = now.minusSeconds(offlineAfterSeconds);
        Instant degradedCutoff = now.minusSeconds(degradedAfterSeconds);

        int movedOffline = repository.markStatusByLastSeenBefore(
                AgentInstanceStatusType.OFFLINE,
                OFFLINE_ELIGIBLE,
                offlineCutoff,
                now
        );
        int movedDegraded = repository.markStatusByLastSeenBefore(
                AgentInstanceStatusType.DEGRADED,
                List.of(AgentInstanceStatusType.ONLINE),
                degradedCutoff,
                now
        );

        if (movedOffline > 0 || movedDegraded > 0) {
            log.info("agent.presence.status_refresh movedOffline={} movedDegraded={} degradedAfter={} offlineAfter={}",
                    movedOffline, movedDegraded, degradedAfterSeconds, offlineAfterSeconds);
        }
    }

    public int heartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    private AgentHeartbeatResponse upsert(AgentAuthContext context, String agentId, String ipHash, AgentHeartbeatRequest request) {
        String resolvedAgentId = normalizeAgentId(agentId, context);
        Instant now = Instant.now();

        AgentInstanceStatus entity = repository.findByTenantIdAndEmpresaIdAndAgentId(
                context.tenantId(),
                context.empresaId(),
                resolvedAgentId
        ).orElseGet(() -> {
            AgentInstanceStatus created = new AgentInstanceStatus();
            created.setTenantId(context.tenantId());
            created.setEmpresaId(context.empresaId());
            created.setAgentId(resolvedAgentId);
            created.setStartedAt(now);
            return created;
        });

        entity.setApiKeyId(context.apiKeyId());
        entity.setKeyPrefix(context.keyPrefix());
        entity.setIpHash(truncate(ipHash, 64));
        entity.setLastSeenAt(now);

        if (request != null) {
            entity.setAgentVersion(truncate(request.agentVersion(), 60));
            entity.setHostname(truncate(request.hostname(), 120));
            entity.setUploadsInFlight(sanitizeNonNegative(request.uploadsInFlight()));
            entity.setPendingFiles(sanitizeNonNegative(request.pendingFiles()));
            entity.setErrorCountWindow(sanitizeNonNegative(request.errorCountWindow()));
            entity.setLastErrorCode(truncate(request.lastErrorCode(), 60));
            entity.setLastErrorMessage(truncate(request.lastErrorMessage(), 500));
            if (request.lastUploadAt() != null) {
                entity.setLastUploadAt(request.lastUploadAt());
            }
            if (request.lastManifestAt() != null) {
                entity.setLastManifestAt(request.lastManifestAt());
            }
        }

        entity.setStatus(resolveStatus(entity, now));
        AgentInstanceStatus saved = repository.save(entity);
        AgentInstanceStatusType effectiveStatus = resolveStatus(saved, now);

        log.info("agent.presence.touch tenantId={} empresaId={} agentId={} status={} apiKeyId={}",
                context.tenantId(), context.empresaId(), resolvedAgentId, effectiveStatus, context.apiKeyId());

        return new AgentHeartbeatResponse(
                context.tenantId(),
                context.empresaId(),
                resolvedAgentId,
                effectiveStatus,
                heartbeatIntervalSeconds,
                now
        );
    }

    private AgentInstanceStatusResponse toResponse(AgentInstanceStatus entity, Instant now) {
        return new AgentInstanceStatusResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmpresaId(),
                entity.getApiKeyId(),
                entity.getKeyPrefix(),
                entity.getAgentId(),
                resolveStatus(entity, now),
                entity.getAgentVersion(),
                entity.getHostname(),
                entity.getUploadsInFlight(),
                entity.getPendingFiles(),
                entity.getErrorCountWindow(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getStartedAt(),
                entity.getLastSeenAt(),
                entity.getLastUploadAt(),
                entity.getLastManifestAt(),
                entity.getUpdatedAt()
        );
    }

    private AgentInstanceStatusType resolveStatus(AgentInstanceStatus entity, Instant now) {
        AgentInstanceStatusType persisted = entity.getStatus();
        if (persisted == AgentInstanceStatusType.REVOKED || persisted == AgentInstanceStatusType.BLOCKED) {
            return persisted;
        }
        if (entity.getLastSeenAt() == null) {
            return AgentInstanceStatusType.OFFLINE;
        }
        if (entity.getLastSeenAt().isBefore(now.minusSeconds(offlineAfterSeconds))) {
            return AgentInstanceStatusType.OFFLINE;
        }
        if (entity.getLastSeenAt().isBefore(now.minusSeconds(degradedAfterSeconds))) {
            return AgentInstanceStatusType.DEGRADED;
        }
        Integer errorCount = entity.getErrorCountWindow();
        if (errorCount != null && errorCount > 0) {
            return AgentInstanceStatusType.DEGRADED;
        }
        return AgentInstanceStatusType.ONLINE;
    }

    private String normalizeAgentId(String rawAgentId, AgentAuthContext context) {
        if (StringUtils.hasText(rawAgentId)) {
            String normalized = rawAgentId.trim();
            if (normalized.length() > 120) {
                throw new ValidationException("X-Agent-Id excede 120 caracteres");
            }
            return normalized;
        }
        if (context == null) {
            throw new ValidationException("agentId obrigatorio");
        }
        String fallback = "api-key-" + context.apiKeyId();
        log.warn("agent.presence.agent_id_missing tenantId={} empresaId={} fallback={}",
                context.tenantId(), context.empresaId(), fallback);
        return fallback;
    }

    private Integer sanitizeNonNegative(Integer value) {
        if (value == null) return null;
        return Math.max(0, value);
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.length() <= limit) return normalized;
        return normalized.substring(0, limit);
    }
}
