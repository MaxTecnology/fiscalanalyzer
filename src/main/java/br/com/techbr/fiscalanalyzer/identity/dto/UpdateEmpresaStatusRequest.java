package br.com.techbr.fiscalanalyzer.identity.dto;

import br.com.techbr.fiscalanalyzer.identity.model.EmpresaStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateEmpresaStatusRequest(
        @NotNull EmpresaStatus status
) {
}
