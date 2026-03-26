package br.com.techbr.fiscalanalyzer.agent.dto;

public record AgentSessionResponse(
        Long tenantId,
        Long empresaId,
        Integer scanIntervalSeconds,
        Integer maxUploadConcurrency,
        Integer heartbeatIntervalSeconds,
        String resolvedAgentId
) {}
