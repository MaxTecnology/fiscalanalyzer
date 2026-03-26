package br.com.techbr.fiscalanalyzer.agent.dto;

import java.time.Instant;

public record AgentInstanceStatusSummaryResponse(
        Long tenantId,
        Long empresaId,
        long total,
        long online,
        long degraded,
        long offline,
        long blocked,
        long revoked,
        Instant generatedAt
) {
}
