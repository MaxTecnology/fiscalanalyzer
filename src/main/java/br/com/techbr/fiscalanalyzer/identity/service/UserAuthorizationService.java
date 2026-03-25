package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserEmpresaRepository;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserAuthorizationService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> READ_ROLES = Set.of("ADMIN", "OPERADOR", "LEITOR");
    private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "OPERADOR");

    private final AppUserEmpresaRepository appUserEmpresaRepository;

    public UserAuthorizationService(AppUserEmpresaRepository appUserEmpresaRepository) {
        this.appUserEmpresaRepository = appUserEmpresaRepository;
    }

    public void assertCanRead(UserAuthContext auth, Long tenantId, Long empresaId) {
        assertAuthenticated(auth);
        assertRole(auth.roles(), READ_ROLES, "Perfil sem permissao de leitura");
        assertScope(auth, tenantId, empresaId);
    }

    public void assertCanWrite(UserAuthContext auth, Long tenantId, Long empresaId) {
        assertAuthenticated(auth);
        assertRole(auth.roles(), WRITE_ROLES, "Perfil sem permissao de escrita");
        assertScope(auth, tenantId, empresaId);
    }

    private void assertAuthenticated(UserAuthContext auth) {
        if (auth == null || auth.userId() == null || auth.userId() <= 0) {
            throw new UnauthorizedException("Contexto de autenticacao do usuario ausente");
        }
    }

    private void assertRole(List<String> roles, Set<String> allowedRoles, String message) {
        if (roles == null || roles.isEmpty()) {
            throw new ForbiddenException(message);
        }
        boolean hasAllowed = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(allowedRoles::contains);
        if (!hasAllowed) {
            throw new ForbiddenException(message);
        }
    }

    private void assertScope(UserAuthContext auth, Long tenantId, Long empresaId) {
        if (tenantId == null || tenantId <= 0) {
            throw new ValidationException("tenantId invalido");
        }
        if (empresaId == null || empresaId <= 0) {
            throw new ValidationException("empresaId invalido");
        }

        boolean isAdmin = auth.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(ROLE_ADMIN::equals);
        if (isAdmin) {
            return;
        }

        boolean allowed = appUserEmpresaRepository.existsByIdAppUserIdAndTenantIdAndIdEmpresaId(
                auth.userId(), tenantId, empresaId);
        if (!allowed) {
            throw new ForbiddenException(
                    "Usuario sem escopo para tenantId/empresaId informado: tenantId=%d empresaId=%d"
                            .formatted(tenantId, empresaId)
            );
        }
    }
}

