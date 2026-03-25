package br.com.techbr.fiscalanalyzer.identity.service;

import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.model.Empresa;
import br.com.techbr.fiscalanalyzer.identity.model.EmpresaStatus;
import br.com.techbr.fiscalanalyzer.identity.model.TenantStatus;
import br.com.techbr.fiscalanalyzer.identity.repository.EmpresaRepository;
import br.com.techbr.fiscalanalyzer.identity.repository.TenantRepository;
import org.springframework.stereotype.Service;

@Service
public class TenantEmpresaValidationService {

    private final TenantRepository tenantRepository;
    private final EmpresaRepository empresaRepository;

    public TenantEmpresaValidationService(TenantRepository tenantRepository,
                                          EmpresaRepository empresaRepository) {
        this.tenantRepository = tenantRepository;
        this.empresaRepository = empresaRepository;
    }

    public void validateAtivo(Long tenantId, Long empresaId) {
        if (tenantId == null || tenantId <= 0 || empresaId == null || empresaId <= 0) {
            throw new ValidationException("tenantId e empresaId sao obrigatorios");
        }

        if (!tenantRepository.existsById(tenantId)) {
            throw new UnprocessableEntityException("tenantId nao encontrado: " + tenantId);
        }
        if (!tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ATIVO)) {
            throw new ForbiddenException("tenantId inativo: " + tenantId);
        }

        Empresa empresa = empresaRepository.findByIdAndTenantId(empresaId, tenantId)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "empresaId nao encontrado para tenantId informado: tenantId=%d empresaId=%d"
                                .formatted(tenantId, empresaId)
                ));

        if (empresa.getStatus() != EmpresaStatus.ATIVA) {
            throw new ForbiddenException("empresaId inativa: " + empresaId);
        }
    }
}
