package br.com.techbr.fiscalanalyzer.identity.dto;

import java.time.Instant;

public record EmpresaResponse(
        Long id,
        Long tenantId,
        String cnpj,
        String razaoSocial,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
