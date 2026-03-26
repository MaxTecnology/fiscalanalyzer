package br.com.techbr.fiscalanalyzer.agent.dto;

import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;

import java.time.Instant;

public record AgentInstanceStatusResponse(
        Long id,
        Long tenantId,
        Long empresaId,
        Long apiKeyId,
        String keyPrefix,
        String agentId,
        AgentInstanceStatusType status,
        String agentVersion,
        String hostname,
        Integer uploadsInFlight,
        Integer pendingFiles,
        Integer errorCountWindow,
        String lastErrorCode,
        String lastErrorMessage,
        Instant startedAt,
        Instant lastSeenAt,
        Instant lastUploadAt,
        Instant lastManifestAt,
        Instant updatedAt
) {
}
