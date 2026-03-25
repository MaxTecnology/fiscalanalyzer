package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.model.Empresa;
import br.com.techbr.fiscalanalyzer.identity.model.EmpresaStatus;
import br.com.techbr.fiscalanalyzer.identity.model.TenantStatus;
import br.com.techbr.fiscalanalyzer.identity.repository.EmpresaRepository;
import br.com.techbr.fiscalanalyzer.identity.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantEmpresaValidationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @InjectMocks
    private TenantEmpresaValidationService service;

    @Test
    void validateAtivo_quandoDadosValidos_naoLanca() {
        Empresa empresa = new Empresa();
        empresa.setTenantId(1L);
        empresa.setStatus(EmpresaStatus.ATIVA);

        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(tenantRepository.existsByIdAndStatus(1L, TenantStatus.ATIVO)).thenReturn(true);
        when(empresaRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(empresa));

        assertDoesNotThrow(() -> service.validateAtivo(1L, 2L));
    }

    @Test
    void validateAtivo_quandoTenantNaoExiste_lanca422() {
        when(tenantRepository.existsById(1L)).thenReturn(false);

        assertThrows(UnprocessableEntityException.class, () -> service.validateAtivo(1L, 2L));
    }

    @Test
    void validateAtivo_quandoTenantInativo_lanca403() {
        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(tenantRepository.existsByIdAndStatus(1L, TenantStatus.ATIVO)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.validateAtivo(1L, 2L));
    }

    @Test
    void validateAtivo_quandoEmpresaNaoExiste_lanca422() {
        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(tenantRepository.existsByIdAndStatus(1L, TenantStatus.ATIVO)).thenReturn(true);
        when(empresaRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(UnprocessableEntityException.class, () -> service.validateAtivo(1L, 2L));
    }

    @Test
    void validateAtivo_quandoEmpresaInativa_lanca403() {
        Empresa empresa = new Empresa();
        empresa.setTenantId(1L);
        empresa.setStatus(EmpresaStatus.INATIVA);

        when(tenantRepository.existsById(1L)).thenReturn(true);
        when(tenantRepository.existsByIdAndStatus(1L, TenantStatus.ATIVO)).thenReturn(true);
        when(empresaRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(empresa));

        assertThrows(ForbiddenException.class, () -> service.validateAtivo(1L, 2L));
    }

    @Test
    void validateAtivo_quandoIdsInvalidos_lanca400() {
        assertThrows(ValidationException.class, () -> service.validateAtivo(0L, 2L));
        assertThrows(ValidationException.class, () -> service.validateAtivo(1L, null));
    }
}
