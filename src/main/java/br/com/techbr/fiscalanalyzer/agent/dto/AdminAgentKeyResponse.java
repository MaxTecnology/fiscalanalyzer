package br.com.techbr.fiscalanalyzer.agent.dto;

import java.time.Instant;

public record AdminAgentKeyResponse(
        Long id,
        Long tenantId,
        Long empresaId,
        String keyPrefix,
        String descricao,
        boolean ativo,
        Instant ultimoUsoAt,
        Instant criadoAt,
        Instant revogadoAt,
        String apiKey
) {}
