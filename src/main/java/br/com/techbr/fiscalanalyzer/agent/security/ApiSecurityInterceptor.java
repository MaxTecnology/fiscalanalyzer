package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.agent.service.AgentApiKeyService;
import br.com.techbr.fiscalanalyzer.agent.service.AgentAuthAuditService;
import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAuditResult;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

@Component
public class ApiSecurityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityInterceptor.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_AGENT_ID = "X-Agent-Id";

    private final AgentApiKeyService agentApiKeyService;
    private final ApiRateLimitService apiRateLimitService;
    private final AgentAuthAuditService agentAuthAuditService;
    private final IdentityAuthService identityAuthService;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    private final List<String> agentProtectedPatterns = List.of(
            "/agent/**",
            "/imports/manifest",
            "/api/importacoes/manifest"
    );
    private final List<String> adminProtectedPatterns = List.of(
            "/admin/**"
    );
    private final List<String> userProtectedPatterns = List.of(
            "/auth/me",
            "/imports/upload",
            "/api/importacoes/upload",
            "/imports",
            "/imports/*",
            "/imports/*/items",
            "/imports/*/failures-summary",
            "/imports/*/reprocess",
            "/documents/**",
            "/sped/**"
    );

    public ApiSecurityInterceptor(AgentApiKeyService agentApiKeyService,
                                  ApiRateLimitService apiRateLimitService,
                                  AgentAuthAuditService agentAuthAuditService,
                                  IdentityAuthService identityAuthService) {
        this.agentApiKeyService = agentApiKeyService;
        this.apiRateLimitService = apiRateLimitService;
        this.agentAuthAuditService = agentAuthAuditService;
        this.identityAuthService = identityAuthService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        if (matches(path, adminProtectedPatterns)) {
            String fingerprint = buildFingerprint(request);
            validateAdmin(request, path, fingerprint);
            return true;
        }

        if (matches(path, agentProtectedPatterns)) {
            String route = apiRateLimitService.resolveRoute(path);
            String fingerprint = buildFingerprint(request);
            AgentAuthContext context;
            try {
                String rawApiKey = extractBearerToken(request, "ApiKey ausente no Authorization");
                context = agentApiKeyService.authenticate(rawApiKey);
            } catch (UnauthorizedException | ForbiddenException ex) {
                try {
                    apiRateLimitService.registerAuthFailure(route, fingerprint, ex.getClass().getSimpleName());
                } catch (TooManyRequestsException rateLimitedEx) {
                    agentAuthAuditService.recordAgentFailure(route, null, fingerprint, AgentAuthAuditResult.RATE_LIMITED, rateLimitedEx.getMessage());
                    throw rateLimitedEx;
                }
                agentAuthAuditService.recordAgentFailure(route, null, fingerprint, toAuditResult(ex), ex.getMessage());
                throw ex;
            }
            apiRateLimitService.registerAuthSuccess(fingerprint);
            try {
                apiRateLimitService.enforceAuthenticated(route, context.apiKeyId(), fingerprint);
            } catch (TooManyRequestsException ex) {
                agentAuthAuditService.recordAgentFailure(route, context, fingerprint, AgentAuthAuditResult.RATE_LIMITED, ex.getMessage());
                throw ex;
            }
            agentAuthAuditService.recordAgentSuccess(route, context, fingerprint);

            log.debug("security.auth.allowed route={} tenantId={} empresaId={} keyPrefix={}",
                    route, context.tenantId(), context.empresaId(), context.keyPrefix());
            request.setAttribute(AgentAuthRequestContext.REQUEST_ATTRIBUTE, context);
            return true;
        }

        if (matches(path, userProtectedPatterns)) {
            String token = extractBearerToken(request, "JWT ausente no Authorization");
            var userContext = identityAuthService.authenticateUserBearer(token);
            request.setAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE, userContext);
            return true;
        }

        return true;
    }

    private void validateAdmin(HttpServletRequest request, String route, String fingerprint) {
        try {
            String token = extractBearerToken(request, "JWT ausente no Authorization");
            var userContext = identityAuthService.authenticateAdminBearer(token);
            request.setAttribute(UserAuthRequestContext.REQUEST_ATTRIBUTE, userContext);
        } catch (UnauthorizedException | ForbiddenException ex) {
            agentAuthAuditService.recordAdminFailure(route, fingerprint, toAuditResult(ex), ex.getMessage());
            throw ex;
        }
    }

    private String extractBearerToken(HttpServletRequest request, String missingTokenMessage) {
        String authorization = request.getHeader(AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            throw new UnauthorizedException("Authorization Bearer ausente");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Formato de Authorization invalido");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException(missingTokenMessage);
        }
        return token;
    }

    private String buildFingerprint(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String userAgent = normalizeHeader(request.getHeader(USER_AGENT_HEADER));
        String agentId = normalizeHeader(request.getHeader(X_AGENT_ID));
        String raw = ip + "|" + userAgent + "|" + agentId;
        return sha256Hex(raw);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwardedFor)) {
            String first = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }

    private String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("security.fingerprint.hash_error message={}", e.getMessage());
            return "fingerprint-error";
        }
    }

    private AgentAuthAuditResult toAuditResult(RuntimeException ex) {
        if (ex instanceof ForbiddenException) {
            return AgentAuthAuditResult.FORBIDDEN;
        }
        return AgentAuthAuditResult.UNAUTHORIZED;
    }

    private boolean matches(String path, List<String> patterns) {
        return patterns.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }
}
