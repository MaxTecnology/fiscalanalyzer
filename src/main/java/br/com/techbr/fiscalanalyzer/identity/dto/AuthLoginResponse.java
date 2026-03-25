package br.com.techbr.fiscalanalyzer.identity.dto;

import java.time.Instant;

public record AuthLoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        AuthUserInfoResponse user
) {
}

