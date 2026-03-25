package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthLoginRequest;
import br.com.techbr.fiscalanalyzer.identity.model.AppUser;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserEmpresa;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserEmpresaId;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserEmpresaRepository;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserRepository;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserRoleRepository;
import br.com.techbr.fiscalanalyzer.identity.security.JwtTokenService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppUserRoleRepository appUserRoleRepository;
    @Mock
    private AppUserEmpresaRepository appUserEmpresaRepository;
    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private IdentityAuthService service;

    @Test
    void login_quandoCredenciaisValidas_retornaToken() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AppUser user = buildUser(7L, "admin@empresa.com", encoder.encode("SenhaForte123"), AppUserStatus.ATIVO);
        when(appUserRepository.findByEmailIgnoreCase("admin@empresa.com")).thenReturn(Optional.of(user));
        when(appUserRoleRepository.findRoleCodesByUserId(7L)).thenReturn(List.of("ADMIN"));

        AppUserEmpresa access = new AppUserEmpresa();
        access.setId(new AppUserEmpresaId(7L, 42L));
        access.setTenantId(9L);
        when(appUserEmpresaRepository.findByIdAppUserIdOrderByIdEmpresaIdAsc(7L)).thenReturn(List.of(access));

        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(jwtTokenService.issueAccessToken(7L, "admin@empresa.com", List.of("ADMIN")))
                .thenReturn(new JwtTokenService.AccessToken("jwt-token", expiresAt));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.login(new AuthLoginRequest("admin@empresa.com", "SenhaForte123"));

        assertEquals("Bearer", response.tokenType());
        assertEquals("jwt-token", response.accessToken());
        assertEquals("ADMIN", response.user().roles().getFirst());
        assertEquals(9L, response.user().empresas().getFirst().tenantId());
        assertEquals(42L, response.user().empresas().getFirst().empresaId());
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void login_quandoSenhaInvalida_lancaUnauthorized() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AppUser user = buildUser(7L, "admin@empresa.com", encoder.encode("SenhaForte123"), AppUserStatus.ATIVO);
        when(appUserRepository.findByEmailIgnoreCase("admin@empresa.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> service.login(new AuthLoginRequest("admin@empresa.com", "senha-errada")));
    }

    @Test
    void login_quandoUsuarioInativo_lancaForbidden() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AppUser user = buildUser(7L, "admin@empresa.com", encoder.encode("SenhaForte123"), AppUserStatus.INATIVO);
        when(appUserRepository.findByEmailIgnoreCase("admin@empresa.com")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> service.login(new AuthLoginRequest("admin@empresa.com", "SenhaForte123")));
    }

    @Test
    void authenticateAdminBearer_quandoNaoTemRoleAdmin_lancaForbidden() {
        AppUser user = buildUser(7L, "operador@empresa.com", "$2a$10$hash", AppUserStatus.ATIVO);
        when(jwtTokenService.parseAndValidateAccessToken("jwt-token"))
                .thenReturn(new JwtTokenService.JwtPrincipal(7L, "operador@empresa.com", List.of("OPERADOR"), Instant.now().plusSeconds(60)));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(appUserRoleRepository.findRoleCodesByUserId(7L)).thenReturn(List.of("OPERADOR"));

        assertThrows(ForbiddenException.class, () -> service.authenticateAdminBearer("jwt-token"));
    }

    @Test
    void me_quandoContextoValido_retornaUsuario() {
        AppUser user = buildUser(7L, "operador@empresa.com", "$2a$10$hash", AppUserStatus.ATIVO);
        user.setLastLoginAt(Instant.now().minusSeconds(10));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(appUserRoleRepository.findRoleCodesByUserId(7L)).thenReturn(List.of("OPERADOR"));
        when(appUserEmpresaRepository.findByIdAppUserIdOrderByIdEmpresaIdAsc(7L)).thenReturn(List.of());

        var response = service.me(new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR")));

        assertEquals(7L, response.id());
        assertEquals("OPERADOR", response.roles().getFirst());
    }

    private AppUser buildUser(Long id, String email, String passwordHash, AppUserStatus status) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setNome("Usuario");
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setStatus(status);
        return user;
    }
}

