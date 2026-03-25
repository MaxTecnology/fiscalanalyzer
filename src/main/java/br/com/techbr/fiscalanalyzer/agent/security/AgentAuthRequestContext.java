package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class AgentAuthRequestContext {

    public static final String REQUEST_ATTRIBUTE = "agentAuthContext";

    private AgentAuthRequestContext() {
    }

    public static AgentAuthContext required(HttpServletRequest request) {
        Object attr = request.getAttribute(REQUEST_ATTRIBUTE);
        if (attr instanceof AgentAuthContext ctx) {
            return ctx;
        }
        throw new UnauthorizedException("Contexto de autenticacao do agente ausente");
    }
}
