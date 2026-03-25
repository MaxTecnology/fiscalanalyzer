package br.com.techbr.fiscalanalyzer.agent.dto;

import jakarta.validation.constraints.Size;

public record AdminCreateAgentKeyRequest(
        @Size(max = 255) String descricao,
        @Size(max = 100) String criadoPor
) {}
