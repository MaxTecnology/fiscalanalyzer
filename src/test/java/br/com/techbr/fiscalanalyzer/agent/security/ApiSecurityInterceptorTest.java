package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.agent.service.AgentApiKeyService;
import br.com.techbr.fiscalanalyzer.agent.service.AgentAuthAuditService;
import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAuditResult;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSecurityInterceptorTest {

    @Mock
    private AgentApiKeyService agentApiKeyService;
    @Mock
    private ApiRateLimitService apiRateLimitService;
    @Mock
    private AgentAuthAuditService agentAuthAuditService;

    @Test
    void agentPath_semBearer_lancaUnauthorized() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        when(apiRateLimitService.resolveRoute("/agent/session")).thenReturn("agent.session");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/session");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(apiRateLimitService).registerAuthFailure(eq("agent.session"), anyString(), eq("UnauthorizedException"));
        verify(agentAuthAuditService).recordAgentFailure(eq("agent.session"), isNull(), anyString(), eq(AgentAuthAuditResult.UNAUTHORIZED), anyString());
    }

    @Test
    void agentPath_comBearer_defineContexto() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        when(apiRateLimitService.resolveRoute("/agent/session")).thenReturn("agent.session");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/session");
        req.addHeader("Authorization", "Bearer fa_live_token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AgentAuthContext ctx = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        when(agentApiKeyService.authenticate("fa_live_token")).thenReturn(ctx);

        boolean allowed = interceptor.preHandle(req, resp, new Object());

        assertTrue(allowed);
        assertSame(ctx, req.getAttribute(AgentAuthRequestContext.REQUEST_ATTRIBUTE));
        verify(apiRateLimitService).registerAuthSuccess(anyString());
        verify(apiRateLimitService).enforceAuthenticated(eq("agent.session"), eq(1L), anyString());
        verify(agentAuthAuditService).recordAgentSuccess(eq("agent.session"), eq(ctx), anyString());
    }

    @Test
    void manifestPath_comBearer_autentica() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        when(apiRateLimitService.resolveRoute("/imports/manifest")).thenReturn("imports.manifest");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/imports/manifest");
        req.addHeader("Authorization", "Bearer fa_live_token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(agentApiKeyService.authenticate("fa_live_token"))
                .thenReturn(new AgentAuthContext(1L, 99L, 42L, "fa_live_ab"));

        assertTrue(interceptor.preHandle(req, resp, new Object()));
    }

    @Test
    void adminPath_semToken_lancaUnauthorized() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/empresas/1/agent-keys");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService).recordAdminFailure(eq("/admin/empresas/1/agent-keys"), anyString(), eq(AgentAuthAuditResult.UNAUTHORIZED), anyString());
    }

    @Test
    void adminPath_tokenInvalido_lancaForbidden() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/empresas/1/agent-keys");
        req.addHeader("X-Admin-Token", "wrong");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(ForbiddenException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService).recordAdminFailure(eq("/admin/empresas/1/agent-keys"), anyString(), eq(AgentAuthAuditResult.FORBIDDEN), anyString());
    }

    @Test
    void adminPath_tokenValido_libera() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/empresas/1/agent-keys");
        req.addHeader("X-Admin-Token", "admin-secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService, never()).recordAdminFailure(anyString(), anyString(), any(), anyString());
    }

    @Test
    void agentPath_rateLimit_lanca429() {
        ApiSecurityInterceptor interceptor = new ApiSecurityInterceptor(agentApiKeyService, apiRateLimitService, agentAuthAuditService, "admin-secret");
        when(apiRateLimitService.resolveRoute("/agent/session")).thenReturn("agent.session");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/session");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doThrow(new TooManyRequestsException("limite", 5))
                .when(apiRateLimitService)
                .registerAuthFailure(eq("agent.session"), anyString(), eq("UnauthorizedException"));

        assertThrows(TooManyRequestsException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService).recordAgentFailure(eq("agent.session"), isNull(), anyString(), eq(AgentAuthAuditResult.RATE_LIMITED), anyString());
    }
}
