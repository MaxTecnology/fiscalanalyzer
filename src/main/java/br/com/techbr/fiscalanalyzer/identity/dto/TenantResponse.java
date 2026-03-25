package br.com.techbr.fiscalanalyzer.identity.dto;

import java.time.Instant;

public record TenantResponse(
        Long id,
        String nome,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
