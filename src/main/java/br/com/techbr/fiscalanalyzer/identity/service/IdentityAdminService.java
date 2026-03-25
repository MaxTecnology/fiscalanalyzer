package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ConflictException;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.dto.*;
import br.com.techbr.fiscalanalyzer.identity.model.*;
import br.com.techbr.fiscalanalyzer.identity.repository.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IdentityAdminService {

    private final TenantRepository tenantRepository;
    private final EmpresaRepository empresaRepository;
    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppUserRoleRepository appUserRoleRepository;
    private final AppUserEmpresaRepository appUserEmpresaRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public IdentityAdminService(TenantRepository tenantRepository,
                                EmpresaRepository empresaRepository,
                                AppUserRepository appUserRepository,
                                AppRoleRepository appRoleRepository,
                                AppUserRoleRepository appUserRoleRepository,
                                AppUserEmpresaRepository appUserEmpresaRepository) {
        this.tenantRepository = tenantRepository;
        this.empresaRepository = empresaRepository;
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appUserRoleRepository = appUserRoleRepository;
        this.appUserEmpresaRepository = appUserEmpresaRepository;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (request == null) {
            throw new ValidationException("Payload obrigatorio");
        }
        String nome = trimToNull(request.nome());
        if (nome == null) {
            throw new ValidationException("nome do tenant e obrigatorio");
        }

        Tenant tenant = new Tenant();
        tenant.setNome(nome);
        tenant.setStatus(TenantStatus.ATIVO);
        tenant = tenantRepository.save(tenant);
        return toTenantResponse(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants(TenantStatus status) {
        List<Tenant> tenants = status == null
                ? tenantRepository.findAllByOrderByIdAsc()
                : tenantRepository.findByStatusOrderByIdAsc(status);
        return tenants.stream().map(this::toTenantResponse).toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long tenantId) {
        return toTenantResponse(loadTenant(tenantId));
    }

    @Transactional
    public TenantResponse updateTenantStatus(Long tenantId, UpdateTenantStatusRequest request) {
        Tenant tenant = loadTenant(tenantId);
        if (request == null || request.status() == null) {
            throw new ValidationException("status do tenant e obrigatorio");
        }
        tenant.setStatus(request.status());
        tenant = tenantRepository.save(tenant);
        return toTenantResponse(tenant);
    }

    @Transactional
    public EmpresaResponse createEmpresa(Long tenantId, CreateEmpresaRequest request) {
        Tenant tenant = loadTenant(tenantId);
        if (tenant.getStatus() != TenantStatus.ATIVO) {
            throw new ForbiddenException("tenantId inativo: " + tenantId);
        }
        if (request == null) {
            throw new ValidationException("Payload obrigatorio");
        }
        String cnpj = normalizeCnpj(request.cnpj());
        String razaoSocial = trimToNull(request.razaoSocial());
        if (razaoSocial == null) {
            throw new ValidationException("razaoSocial obrigatoria");
        }
        if (empresaRepository.existsByTenantIdAndCnpj(tenantId, cnpj)) {
            throw new ConflictException("CNPJ ja cadastrado para o tenant informado");
        }

        Empresa empresa = new Empresa();
        empresa.setTenantId(tenantId);
        empresa.setCnpj(cnpj);
        empresa.setRazaoSocial(razaoSocial);
        empresa.setStatus(EmpresaStatus.ATIVA);
        empresa = empresaRepository.save(empresa);
        return toEmpresaResponse(empresa);
    }

    @Transactional(readOnly = true)
    public List<EmpresaResponse> listEmpresas(Long tenantId, EmpresaStatus status) {
        ensurePositiveId(tenantId, "tenantId");
        if (!tenantRepository.existsById(tenantId)) {
            throw new UnprocessableEntityException("tenantId nao encontrado: " + tenantId);
        }
        List<Empresa> empresas = status == null
                ? empresaRepository.findByTenantIdOrderByIdAsc(tenantId)
                : empresaRepository.findByTenantIdAndStatusOrderByIdAsc(tenantId, status);
        return empresas.stream().map(this::toEmpresaResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmpresaResponse getEmpresa(Long tenantId, Long empresaId) {
        return toEmpresaResponse(loadEmpresa(tenantId, empresaId));
    }

    @Transactional
    public EmpresaResponse updateEmpresaStatus(Long tenantId, Long empresaId, UpdateEmpresaStatusRequest request) {
        Empresa empresa = loadEmpresa(tenantId, empresaId);
        if (request == null || request.status() == null) {
            throw new ValidationException("status da empresa e obrigatorio");
        }
        empresa.setStatus(request.status());
        empresa = empresaRepository.save(empresa);
        return toEmpresaResponse(empresa);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request == null) {
            throw new ValidationException("Payload obrigatorio");
        }
        String nome = trimToNull(request.nome());
        String email = normalizeEmail(request.email());
        if (nome == null) {
            throw new ValidationException("nome do usuario e obrigatorio");
        }
        if (email == null) {
            throw new ValidationException("email do usuario e obrigatorio");
        }
        if (appUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("email ja cadastrado");
        }

        AppUser user = new AppUser();
        user.setNome(nome);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(request.status() == null ? AppUserStatus.ATIVO : request.status());

        user = appUserRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(AppUserStatus status) {
        List<AppUser> users = status == null
                ? appUserRepository.findAllByOrderByIdAsc()
                : appUserRepository.findByStatusOrderByIdAsc(status);
        return users.stream().map(this::toUserResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        AppUser user = loadUser(userId);
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        AppUser user = loadUser(userId);
        if (request == null || request.status() == null) {
            throw new ValidationException("status do usuario e obrigatorio");
        }
        user.setStatus(request.status());
        user = appUserRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse addRole(Long userId, String roleCode) {
        AppUser user = loadUser(userId);
        AppRole role = loadRole(roleCode);
        AppUserRoleId id = new AppUserRoleId(user.getId(), role.getId());
        if (!appUserRoleRepository.existsById(id)) {
            AppUserRole relation = new AppUserRole();
            relation.setId(id);
            appUserRoleRepository.save(relation);
        }
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse removeRole(Long userId, String roleCode) {
        AppUser user = loadUser(userId);
        AppRole role = loadRole(roleCode);
        AppUserRoleId id = new AppUserRoleId(user.getId(), role.getId());
        if (appUserRoleRepository.existsById(id)) {
            appUserRoleRepository.deleteById(id);
        }
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse addEmpresaAccess(Long userId, Long tenantId, Long empresaId) {
        AppUser user = loadUser(userId);
        Tenant tenant = loadTenant(tenantId);
        if (tenant.getStatus() != TenantStatus.ATIVO) {
            throw new ForbiddenException("tenantId inativo: " + tenantId);
        }
        Empresa empresa = loadEmpresa(tenantId, empresaId);
        if (empresa.getStatus() != EmpresaStatus.ATIVA) {
            throw new ForbiddenException("empresaId inativa: " + empresaId);
        }

        AppUserEmpresaId id = new AppUserEmpresaId(user.getId(), empresaId);
        if (!appUserEmpresaRepository.existsById(id)) {
            AppUserEmpresa relation = new AppUserEmpresa();
            relation.setId(id);
            relation.setTenantId(tenantId);
            appUserEmpresaRepository.save(relation);
        }
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse removeEmpresaAccess(Long userId, Long tenantId, Long empresaId) {
        AppUser user = loadUser(userId);
        AppUserEmpresaId id = new AppUserEmpresaId(user.getId(), empresaId);
        appUserEmpresaRepository.findById(id).ifPresent(link -> {
            if (!tenantId.equals(link.getTenantId())) {
                throw new ValidationException("tenantId nao confere com o vinculo usuario-empresa");
            }
            appUserEmpresaRepository.deleteById(id);
        });
        return toUserResponse(user);
    }

    private Tenant loadTenant(Long tenantId) {
        ensurePositiveId(tenantId, "tenantId");
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new UnprocessableEntityException("tenantId nao encontrado: " + tenantId));
    }

    private Empresa loadEmpresa(Long tenantId, Long empresaId) {
        ensurePositiveId(tenantId, "tenantId");
        ensurePositiveId(empresaId, "empresaId");
        return empresaRepository.findByIdAndTenantId(empresaId, tenantId)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "empresaId nao encontrado para tenantId informado: tenantId=%d empresaId=%d"
                                .formatted(tenantId, empresaId)));
    }

    private AppUser loadUser(Long userId) {
        ensurePositiveId(userId, "userId");
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new UnprocessableEntityException("userId nao encontrado: " + userId));
    }

    private AppRole loadRole(String roleCode) {
        String trimmed = trimToNull(roleCode);
        if (trimmed == null) {
            throw new ValidationException("roleCode obrigatorio");
        }
        final String normalized = trimmed.toUpperCase(Locale.ROOT);
        return appRoleRepository.findByCodigo(normalized)
                .orElseThrow(() -> new UnprocessableEntityException("roleCode nao encontrado: " + normalized));
    }

    private TenantResponse toTenantResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getNome(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }

    private EmpresaResponse toEmpresaResponse(Empresa empresa) {
        return new EmpresaResponse(
                empresa.getId(),
                empresa.getTenantId(),
                empresa.getCnpj(),
                empresa.getRazaoSocial(),
                empresa.getStatus().name(),
                empresa.getCreatedAt(),
                empresa.getUpdatedAt()
        );
    }

    private UserResponse toUserResponse(AppUser user) {
        List<String> roles = Optional.ofNullable(appUserRoleRepository.findRoleCodesByUserId(user.getId()))
                .orElseGet(List::of);
        List<UserEmpresaAccessResponse> empresas = Optional.ofNullable(
                        appUserEmpresaRepository.findByIdAppUserIdOrderByIdEmpresaIdAsc(user.getId()))
                .orElseGet(List::of)
                .stream()
                .map(link -> new UserEmpresaAccessResponse(link.getTenantId(), link.getId().getEmpresaId()))
                .toList();

        return new UserResponse(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                roles,
                empresas
        );
    }

    private String normalizeCnpj(String cnpj) {
        String raw = trimToNull(cnpj);
        if (raw == null) {
            throw new ValidationException("cnpj obrigatorio");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new ValidationException("cnpj invalido: deve conter 14 digitos");
        }
        return digits;
    }

    private String normalizeEmail(String email) {
        String value = trimToNull(email);
        if (value == null) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private void ensurePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new ValidationException(fieldName + " invalido");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
