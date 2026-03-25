package br.com.techbr.fiscalanalyzer.identity.security;

import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class UserAuthRequestContext {

    public static final String REQUEST_ATTRIBUTE = "USER_AUTH_CONTEXT";

    private UserAuthRequestContext() {
    }

    public static UserAuthContext required(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof UserAuthContext context) {
            return context;
        }
        throw new UnauthorizedException("Contexto de autenticacao do usuario ausente");
    }
}

