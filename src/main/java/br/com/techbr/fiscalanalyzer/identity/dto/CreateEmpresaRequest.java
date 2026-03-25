package br.com.techbr.fiscalanalyzer.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmpresaRequest(
        @NotBlank @Size(max = 20) String cnpj,
        @NotBlank @Size(max = 200) String razaoSocial
) {
}
