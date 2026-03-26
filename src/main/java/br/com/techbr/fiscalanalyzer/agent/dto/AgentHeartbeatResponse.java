package br.com.techbr.fiscalanalyzer.agent.dto;

import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;

import java.time.Instant;

public record AgentHeartbeatResponse(
        Long tenantId,
        Long empresaId,
        String agentId,
        AgentInstanceStatusType status,
        Integer heartbeatIntervalSeconds,
        Instant serverTime
) {
}
