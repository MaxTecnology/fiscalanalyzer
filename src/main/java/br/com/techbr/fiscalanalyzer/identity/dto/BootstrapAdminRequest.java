package br.com.techbr.fiscalanalyzer.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BootstrapAdminRequest(
        @NotBlank @Size(max = 150) String tenantNome,
        @NotBlank @Size(max = 200) String empresaRazaoSocial,
        @NotBlank String empresaCnpj,
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}

