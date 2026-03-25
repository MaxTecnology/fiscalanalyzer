package br.com.techbr.fiscalanalyzer.identity.dto;

public record BootstrapAdminResponse(
        Long tenantId,
        Long empresaId,
        Long userId,
        String email,
        String message
) {
}

