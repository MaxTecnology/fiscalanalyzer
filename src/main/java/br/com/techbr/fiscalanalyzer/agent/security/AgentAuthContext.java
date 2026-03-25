package br.com.techbr.fiscalanalyzer.agent.security;

public record AgentAuthContext(
        Long apiKeyId,
        Long tenantId,
        Long empresaId,
        String keyPrefix
) {}
