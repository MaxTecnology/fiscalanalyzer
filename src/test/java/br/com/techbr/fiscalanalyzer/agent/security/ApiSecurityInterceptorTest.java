package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.agent.service.AgentApiKeyService;
import br.com.techbr.fiscalanalyzer.agent.service.AgentAuthAuditService;
import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAuditResult;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

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
    @Mock
    private IdentityAuthService identityAuthService;

    private ApiSecurityInterceptor interceptor() {
        return new ApiSecurityInterceptor(
                agentApiKeyService,
                apiRateLimitService,
                agentAuthAuditService,
                identityAuthService
        );
    }

    @Test
    void agentPath_semBearer_lancaUnauthorized() {
        ApiSecurityInterceptor interceptor = interceptor();
        when(apiRateLimitService.resolveRoute("/agent/session")).thenReturn("agent.session");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/session");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(apiRateLimitService).registerAuthFailure(eq("agent.session"), anyString(), eq("UnauthorizedException"));
        verify(agentAuthAuditService).recordAgentFailure(eq("agent.session"), isNull(), anyString(), eq(AgentAuthAuditResult.UNAUTHORIZED), anyString());
    }

    @Test
    void agentPath_comBearer_defineContexto() {
        ApiSecurityInterceptor interceptor = interceptor();
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
        ApiSecurityInterceptor interceptor = interceptor();
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
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/empresas/1/agent-keys");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService).recordAdminFailure(eq("/admin/empresas/1/agent-keys"), anyString(), eq(AgentAuthAuditResult.UNAUTHORIZED), anyString());
    }

    @Test
    void adminPath_bearerInvalido_lancaForbidden() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/empresas/1/agent-keys");
        req.addHeader("Authorization", "Bearer invalid-jwt");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(identityAuthService.authenticateAdminBearer("invalid-jwt"))
                .thenThrow(new ForbiddenException("Acesso negado"));

        assertThrows(ForbiddenException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService).recordAdminFailure(eq("/admin/empresas/1/agent-keys"), anyString(), eq(AgentAuthAuditResult.FORBIDDEN), anyString());
    }

    @Test
    void adminPath_bearerValido_libera() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/empresas/1/agent-keys");
        req.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        UserAuthContext context = new UserAuthContext(10L, "admin@empresa.com", List.of("ADMIN"));
        when(identityAuthService.authenticateAdminBearer("jwt-token")).thenReturn(context);

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertSame(context, req.getAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE));
        verify(agentAuthAuditService, never()).recordAdminFailure(anyString(), anyString(), any(), anyString());
    }

    @Test
    void agentPath_rateLimit_lanca429() {
        ApiSecurityInterceptor interceptor = interceptor();
        when(apiRateLimitService.resolveRoute("/agent/session")).thenReturn("agent.session");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/session");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doThrow(new TooManyRequestsException("limite", 5))
                .when(apiRateLimitService)
                .registerAuthFailure(eq("agent.session"), anyString(), eq("UnauthorizedException"));

        assertThrows(TooManyRequestsException.class, () -> interceptor.preHandle(req, resp, new Object()));
        verify(agentAuthAuditService).recordAgentFailure(eq("agent.session"), isNull(), anyString(), eq(AgentAuthAuditResult.RATE_LIMITED), anyString());
    }

    @Test
    void adminPath_bearerJwtValido_libera() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/tenants");
        req.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        UserAuthContext context = new UserAuthContext(10L, "admin@empresa.com", List.of("ADMIN"));
        when(identityAuthService.authenticateAdminBearer("jwt-token")).thenReturn(context);

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertSame(context, req.getAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE));
        verify(agentAuthAuditService, never()).recordAdminFailure(anyString(), anyString(), any(), anyString());
    }

    @Test
    void authMe_bearerJwtValido_defineContexto() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/auth/me");
        req.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        UserAuthContext context = new UserAuthContext(7L, "user@empresa.com", List.of("OPERADOR"));
        when(identityAuthService.authenticateUserBearer("jwt-token")).thenReturn(context);

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertSame(context, req.getAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE));
    }

    @Test
    void importsRead_semBearer_lancaUnauthorized() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/imports/1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(req, resp, new Object()));
    }

    @Test
    void importsList_bearerJwtValido_defineContexto() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/imports");
        req.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        UserAuthContext context = new UserAuthContext(9L, "operador@empresa.com", List.of("OPERADOR"));
        when(identityAuthService.authenticateUserBearer("jwt-token")).thenReturn(context);

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertSame(context, req.getAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE));
    }

    @Test
    void importsUpload_bearerJwtValido_defineContexto() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/imports/upload");
        req.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        UserAuthContext context = new UserAuthContext(9L, "operador@empresa.com", List.of("OPERADOR"));
        when(identityAuthService.authenticateUserBearer("jwt-token")).thenReturn(context);

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertSame(context, req.getAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE));
    }

    @Test
    void adminPath_xAdminToken_semBearer_lancaUnauthorized() {
        ApiSecurityInterceptor interceptor = interceptor();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin/tenants");
        req.addHeader("X-Admin-Token", "admin-secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(req, resp, new Object()));
    }
}
