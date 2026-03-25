package br.com.techbr.fiscalanalyzer.identity.security;

import java.util.List;

public record UserAuthContext(
        Long userId,
        String email,
        List<String> roles
) {
}

