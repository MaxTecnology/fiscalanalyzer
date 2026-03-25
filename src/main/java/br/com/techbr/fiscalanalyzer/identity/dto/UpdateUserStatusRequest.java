package br.com.techbr.fiscalanalyzer.identity.dto;

import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull AppUserStatus status
) {
}
