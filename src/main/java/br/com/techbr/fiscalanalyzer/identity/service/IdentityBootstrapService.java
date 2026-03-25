package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.dto.BootstrapAdminRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.BootstrapAdminResponse;
import br.com.techbr.fiscalanalyzer.identity.model.*;
import br.com.techbr.fiscalanalyzer.identity.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class IdentityBootstrapService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final TenantRepository tenantRepository;
    private final EmpresaRepository empresaRepository;
    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppUserRoleRepository appUserRoleRepository;
    private final AppUserEmpresaRepository appUserEmpresaRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String bootstrapToken;

    public IdentityBootstrapService(TenantRepository tenantRepository,
                                    EmpresaRepository empresaRepository,
                                    AppUserRepository appUserRepository,
                                    AppRoleRepository appRoleRepository,
                                    AppUserRoleRepository appUserRoleRepository,
                                    AppUserEmpresaRepository appUserEmpresaRepository,
                                    @Value("${app.security.bootstrap-token:}") String bootstrapToken) {
        this.tenantRepository = tenantRepository;
        this.empresaRepository = empresaRepository;
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appUserRoleRepository = appUserRoleRepository;
        this.appUserEmpresaRepository = appUserEmpresaRepository;
        this.bootstrapToken = bootstrapToken;
    }

    @Transactional
    public BootstrapAdminResponse bootstrap(String providedToken, BootstrapAdminRequest request) {
        validateBootstrapToken(providedToken);
        if (request == null) {
            throw new ValidationException("Payload obrigatorio");
        }
        if (appUserRepository.count() > 0) {
            throw new ForbiddenException("Bootstrap indisponivel: usuarios ja cadastrados");
        }

        String email = normalizeEmail(request.email());
        String cnpj = normalizeCnpj(request.empresaCnpj());
        String tenantNome = trimToNull(request.tenantNome());
        String razaoSocial = trimToNull(request.empresaRazaoSocial());
        String nome = trimToNull(request.nome());
        String password = trimToNull(request.password());

        if (tenantNome == null) {
            throw new ValidationException("tenantNome obrigatorio");
        }
        if (razaoSocial == null) {
            throw new ValidationException("empresaRazaoSocial obrigatoria");
        }
        if (nome == null) {
            throw new ValidationException("nome obrigatorio");
        }
        if (email == null) {
            throw new ValidationException("email obrigatorio");
        }
        if (password == null) {
            throw new ValidationException("password obrigatorio");
        }

        Tenant tenant = new Tenant();
        tenant.setNome(tenantNome);
        tenant.setStatus(TenantStatus.ATIVO);
        tenant = tenantRepository.save(tenant);

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenant.getId());
        empresa.setCnpj(cnpj);
        empresa.setRazaoSocial(razaoSocial);
        empresa.setStatus(EmpresaStatus.ATIVA);
        empresa = empresaRepository.save(empresa);

        AppUser user = new AppUser();
        user.setNome(nome);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(AppUserStatus.ATIVO);
        user = appUserRepository.save(user);

        AppRole adminRole = appRoleRepository.findByCodigo(ADMIN_ROLE)
                .orElseThrow(() -> new ValidationException("Role ADMIN nao encontrada"));

        AppUserRole userRole = new AppUserRole();
        userRole.setId(new AppUserRoleId(user.getId(), adminRole.getId()));
        appUserRoleRepository.save(userRole);

        AppUserEmpresa userEmpresa = new AppUserEmpresa();
        userEmpresa.setId(new AppUserEmpresaId(user.getId(), empresa.getId()));
        userEmpresa.setTenantId(tenant.getId());
        appUserEmpresaRepository.save(userEmpresa);

        return new BootstrapAdminResponse(
                tenant.getId(),
                empresa.getId(),
                user.getId(),
                user.getEmail(),
                "Bootstrap concluido. Use /auth/login para obter JWT."
        );
    }

    private void validateBootstrapToken(String providedToken) {
        if (!StringUtils.hasText(bootstrapToken)) {
            throw new ForbiddenException("Bootstrap desabilitado: APP_SECURITY_BOOTSTRAP_TOKEN nao configurado");
        }
        if (!StringUtils.hasText(providedToken)) {
            throw new UnauthorizedException("X-Bootstrap-Token ausente");
        }
        if (!bootstrapToken.equals(providedToken)) {
            throw new ForbiddenException("X-Bootstrap-Token invalido");
        }
    }

    private String normalizeEmail(String email) {
        String value = trimToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeCnpj(String cnpj) {
        String raw = trimToNull(cnpj);
        if (raw == null) {
            throw new ValidationException("empresaCnpj obrigatorio");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new ValidationException("empresaCnpj invalido: deve conter 14 digitos");
        }
        return digits;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

