package br.com.techbr.fiscalanalyzer.identity.dto;

import java.time.Instant;
import java.util.List;

public record AuthUserInfoResponse(
        Long id,
        String nome,
        String email,
        String status,
        Instant lastLoginAt,
        List<String> roles,
        List<UserEmpresaAccessResponse> empresas
) {
}

