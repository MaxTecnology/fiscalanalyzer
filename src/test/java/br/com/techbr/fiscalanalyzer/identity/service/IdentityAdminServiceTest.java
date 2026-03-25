package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ConflictException;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.identity.dto.CreateEmpresaRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.CreateTenantRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.CreateUserRequest;
import br.com.techbr.fiscalanalyzer.identity.model.*;
import br.com.techbr.fiscalanalyzer.identity.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAdminServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppRoleRepository appRoleRepository;
    @Mock
    private AppUserRoleRepository appUserRoleRepository;
    @Mock
    private AppUserEmpresaRepository appUserEmpresaRepository;

    @InjectMocks
    private IdentityAdminService service;

    @Test
    void createTenant_criaComStatusAtivo() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", 10L);
            return tenant;
        });

        var response = service.createTenant(new CreateTenantRequest("Cliente A"));

        assertEquals(10L, response.id());
        assertEquals("Cliente A", response.nome());
        assertEquals("ATIVO", response.status());
    }

    @Test
    void createEmpresa_rejeitaCnpjDuplicado() {
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", 1L);
        tenant.setStatus(TenantStatus.ATIVO);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(empresaRepository.existsByTenantIdAndCnpj(1L, "12345678000199")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createEmpresa(1L, new CreateEmpresaRequest("12.345.678/0001-99", "Empresa A")));
    }

    @Test
    void createUser_salvaComPasswordHash() {
        when(appUserRepository.findByEmailIgnoreCase("user@empresa.com")).thenReturn(Optional.empty());
        when(appUserRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of());
        when(appUserEmpresaRepository.findByIdAppUserIdOrderByIdEmpresaIdAsc(1L)).thenReturn(List.of());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        var response = service.createUser(
                new CreateUserRequest("Operador", "user@empresa.com", "SenhaForte123", null));

        assertEquals(1L, response.id());
        assertEquals("ATIVO", response.status());
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertNotNull(saved.getPasswordHash());
        assertNotEquals("SenhaForte123", saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
    }

    @Test
    void addRole_quandoRoleNaoExiste_lanca422() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(appRoleRepository.findByCodigo("ADMIN")).thenReturn(Optional.empty());

        assertThrows(UnprocessableEntityException.class, () -> service.addRole(7L, "admin"));
    }

    @Test
    void addEmpresaAccess_quandoEmpresaInativa_lanca403() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 3L);
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(user));

        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", 1L);
        tenant.setStatus(TenantStatus.ATIVO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Empresa empresa = new Empresa();
        ReflectionTestUtils.setField(empresa, "id", 8L);
        empresa.setTenantId(1L);
        empresa.setStatus(EmpresaStatus.INATIVA);
        when(empresaRepository.findByIdAndTenantId(8L, 1L)).thenReturn(Optional.of(empresa));

        assertThrows(ForbiddenException.class, () -> service.addEmpresaAccess(3L, 1L, 8L));
    }

    @Test
    void addEmpresaAccess_quandoTenantInativo_lanca403() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 3L);
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(user));

        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", 1L);
        tenant.setStatus(TenantStatus.INATIVO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThrows(ForbiddenException.class, () -> service.addEmpresaAccess(3L, 1L, 8L));
    }
}
