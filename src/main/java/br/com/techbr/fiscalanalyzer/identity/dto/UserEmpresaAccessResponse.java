package br.com.techbr.fiscalanalyzer.identity.dto;

public record UserEmpresaAccessResponse(
        Long tenantId,
        Long empresaId
) {
}
