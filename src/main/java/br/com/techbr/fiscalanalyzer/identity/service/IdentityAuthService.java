package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthLoginRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthLoginResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthUserInfoResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.UserEmpresaAccessResponse;
import br.com.techbr.fiscalanalyzer.identity.model.AppUser;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserEmpresaRepository;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserRepository;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserRoleRepository;
import br.com.techbr.fiscalanalyzer.identity.security.JwtTokenService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class IdentityAuthService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AppUserRepository appUserRepository;
    private final AppUserRoleRepository appUserRoleRepository;
    private final AppUserEmpresaRepository appUserEmpresaRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public IdentityAuthService(AppUserRepository appUserRepository,
                               AppUserRoleRepository appUserRoleRepository,
                               AppUserEmpresaRepository appUserEmpresaRepository,
                               JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.appUserRoleRepository = appUserRoleRepository;
        this.appUserEmpresaRepository = appUserEmpresaRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        if (request == null) {
            throw new ValidationException("Payload obrigatorio");
        }

        String email = normalizeEmail(request.email());
        String password = trimToNull(request.password());
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new ValidationException("email e password sao obrigatorios");
        }

        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Credenciais invalidas"));

        if (user.getStatus() != AppUserStatus.ATIVO) {
            throw new ForbiddenException("Usuario inativo");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Credenciais invalidas");
        }

        List<String> roles = loadRoles(user.getId());
        if (roles.isEmpty()) {
            throw new ForbiddenException("Usuario sem perfil de acesso");
        }

        user.setLastLoginAt(Instant.now());
        user = appUserRepository.save(user);

        JwtTokenService.AccessToken accessToken = jwtTokenService.issueAccessToken(user.getId(), user.getEmail(), roles);
        return new AuthLoginResponse(
                accessToken.token(),
                "Bearer",
                accessToken.expiresAt(),
                toUserInfo(user, roles)
        );
    }

    @Transactional(readOnly = true)
    public UserAuthContext authenticateUserBearer(String jwtToken) {
        JwtTokenService.JwtPrincipal principal = jwtTokenService.parseAndValidateAccessToken(jwtToken);
        AppUser user = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Usuario nao encontrado"));

        if (user.getStatus() != AppUserStatus.ATIVO) {
            throw new ForbiddenException("Usuario inativo");
        }
        if (!user.getEmail().equalsIgnoreCase(principal.email())) {
            throw new UnauthorizedException("JWT invalido para usuario atual");
        }

        List<String> roles = loadRoles(user.getId());
        return new UserAuthContext(user.getId(), user.getEmail(), roles);
    }

    @Transactional(readOnly = true)
    public UserAuthContext authenticateAdminBearer(String jwtToken) {
        UserAuthContext context = authenticateUserBearer(jwtToken);
        boolean isAdmin = context.roles().stream().anyMatch(role -> ADMIN_ROLE.equalsIgnoreCase(role));
        if (!isAdmin) {
            throw new ForbiddenException("Perfil ADMIN requerido");
        }
        return context;
    }

    @Transactional(readOnly = true)
    public AuthUserInfoResponse me(UserAuthContext context) {
        if (context == null || context.userId() == null) {
            throw new UnauthorizedException("Contexto de autenticacao ausente");
        }
        AppUser user = appUserRepository.findById(context.userId())
                .orElseThrow(() -> new UnprocessableEntityException("userId nao encontrado: " + context.userId()));
        if (user.getStatus() != AppUserStatus.ATIVO) {
            throw new ForbiddenException("Usuario inativo");
        }
        List<String> roles = loadRoles(user.getId());
        return toUserInfo(user, roles);
    }

    private AuthUserInfoResponse toUserInfo(AppUser user, List<String> roles) {
        List<UserEmpresaAccessResponse> empresas = appUserEmpresaRepository
                .findByIdAppUserIdOrderByIdEmpresaIdAsc(user.getId())
                .stream()
                .map(link -> new UserEmpresaAccessResponse(link.getTenantId(), link.getId().getEmpresaId()))
                .toList();

        return new AuthUserInfoResponse(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getStatus().name(),
                user.getLastLoginAt(),
                roles,
                empresas
        );
    }

    private List<String> loadRoles(Long userId) {
        return appUserRoleRepository.findRoleCodesByUserId(userId)
                .stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String normalizeEmail(String email) {
        String value = trimToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

