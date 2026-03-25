package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.identity.dto.BootstrapAdminRequest;
import br.com.techbr.fiscalanalyzer.identity.model.*;
import br.com.techbr.fiscalanalyzer.identity.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityBootstrapServiceTest {

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

    @Test
    void bootstrap_quandoValido_criaTenantEmpresaUserAdmin() {
        IdentityBootstrapService service = new IdentityBootstrapService(
                tenantRepository,
                empresaRepository,
                appUserRepository,
                appRoleRepository,
                appUserRoleRepository,
                appUserEmpresaRepository,
                "bootstrap-secret"
        );

        when(appUserRepository.count()).thenReturn(0L);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", 10L);
            return tenant;
        });
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(invocation -> {
            Empresa empresa = invocation.getArgument(0);
            ReflectionTestUtils.setField(empresa, "id", 20L);
            return empresa;
        });
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 30L);
            return user;
        });

        AppRole adminRole = new AppRole();
        ReflectionTestUtils.setField(adminRole, "id", 1L);
        adminRole.setCodigo("ADMIN");
        when(appRoleRepository.findByCodigo("ADMIN")).thenReturn(Optional.of(adminRole));

        var response = service.bootstrap("bootstrap-secret", new BootstrapAdminRequest(
                "Tenant A",
                "Empresa A LTDA",
                "12345678000199",
                "Admin A",
                "admin@empresa.com",
                "SenhaForte123"
        ));

        assertEquals(10L, response.tenantId());
        assertEquals(20L, response.empresaId());
        assertEquals(30L, response.userId());
        assertEquals("admin@empresa.com", response.email());
    }

    @Test
    void bootstrap_quandoTokenInvalido_lancaForbidden() {
        IdentityBootstrapService service = new IdentityBootstrapService(
                tenantRepository,
                empresaRepository,
                appUserRepository,
                appRoleRepository,
                appUserRoleRepository,
                appUserEmpresaRepository,
                "bootstrap-secret"
        );

        assertThrows(ForbiddenException.class, () -> service.bootstrap("wrong", new BootstrapAdminRequest(
                "Tenant A", "Empresa A LTDA", "12345678000199", "Admin A", "admin@empresa.com", "SenhaForte123"
        )));
    }

    @Test
    void bootstrap_quandoTokenAusente_lancaUnauthorized() {
        IdentityBootstrapService service = new IdentityBootstrapService(
                tenantRepository,
                empresaRepository,
                appUserRepository,
                appRoleRepository,
                appUserRoleRepository,
                appUserEmpresaRepository,
                "bootstrap-secret"
        );

        assertThrows(UnauthorizedException.class, () -> service.bootstrap(null, new BootstrapAdminRequest(
                "Tenant A", "Empresa A LTDA", "12345678000199", "Admin A", "admin@empresa.com", "SenhaForte123"
        )));
    }

    @Test
    void bootstrap_quandoJaExisteUsuario_lancaForbidden() {
        IdentityBootstrapService service = new IdentityBootstrapService(
                tenantRepository,
                empresaRepository,
                appUserRepository,
                appRoleRepository,
                appUserRoleRepository,
                appUserEmpresaRepository,
                "bootstrap-secret"
        );
        when(appUserRepository.count()).thenReturn(1L);

        assertThrows(ForbiddenException.class, () -> service.bootstrap("bootstrap-secret", new BootstrapAdminRequest(
                "Tenant A", "Empresa A LTDA", "12345678000199", "Admin A", "admin@empresa.com", "SenhaForte123"
        )));
    }
}

