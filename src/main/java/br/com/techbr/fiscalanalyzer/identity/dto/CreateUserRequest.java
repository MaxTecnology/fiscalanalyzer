package br.com.techbr.fiscalanalyzer.identity.dto;

import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        AppUserStatus status
) {
}
