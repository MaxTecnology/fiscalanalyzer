package br.com.techbr.fiscalanalyzer.agent.dto;

import java.time.Instant;

public record AgentHeartbeatRequest(
        String agentVersion,
        String hostname,
        Integer uploadsInFlight,
        Integer pendingFiles,
        Integer errorCountWindow,
        String lastErrorCode,
        String lastErrorMessage,
        Instant lastUploadAt,
        Instant lastManifestAt
) {
}
