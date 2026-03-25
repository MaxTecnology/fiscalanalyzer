package br.com.techbr.fiscalanalyzer.identity.dto;

import br.com.techbr.fiscalanalyzer.identity.model.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(
        @NotNull TenantStatus status
) {
}
