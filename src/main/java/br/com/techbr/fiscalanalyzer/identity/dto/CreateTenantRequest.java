package br.com.techbr.fiscalanalyzer.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 150) String nome
) {
}
