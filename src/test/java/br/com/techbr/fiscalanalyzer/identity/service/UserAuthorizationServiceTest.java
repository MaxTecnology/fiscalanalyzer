package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.identity.repository.AppUserEmpresaRepository;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthorizationServiceTest {

    @Mock
    private AppUserEmpresaRepository appUserEmpresaRepository;

    @InjectMocks
    private UserAuthorizationService service;

    @Test
    void assertCanRead_quandoLeitorComEscopo_valido() {
        UserAuthContext auth = new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"));
        when(appUserEmpresaRepository.existsByIdAppUserIdAndTenantIdAndIdEmpresaId(7L, 1L, 2L))
                .thenReturn(true);

        service.assertCanRead(auth, 1L, 2L);

        verify(appUserEmpresaRepository).existsByIdAppUserIdAndTenantIdAndIdEmpresaId(7L, 1L, 2L);
    }

    @Test
    void assertCanWrite_quandoLeitor_semPermissao() {
        UserAuthContext auth = new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"));

        assertThrows(ForbiddenException.class, () -> service.assertCanWrite(auth, 1L, 2L));
    }

    @Test
    void assertCanRead_quandoSemEscopo_lancaForbidden() {
        UserAuthContext auth = new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"));
        when(appUserEmpresaRepository.existsByIdAppUserIdAndTenantIdAndIdEmpresaId(7L, 1L, 2L))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.assertCanRead(auth, 1L, 2L));
    }

    @Test
    void assertCanWrite_adminNaoExigeEscopo() {
        UserAuthContext auth = new UserAuthContext(1L, "admin@empresa.com", List.of("ADMIN"));

        service.assertCanWrite(auth, 1L, 2L);
    }
}

