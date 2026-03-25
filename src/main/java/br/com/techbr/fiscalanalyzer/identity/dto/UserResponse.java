package br.com.techbr.fiscalanalyzer.identity.dto;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String nome,
        String email,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        List<String> roles,
        List<UserEmpresaAccessResponse> empresas
) {
}
