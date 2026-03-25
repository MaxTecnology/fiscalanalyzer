package br.com.techbr.fiscalanalyzer.identity.repository;

import br.com.techbr.fiscalanalyzer.identity.model.Empresa;
import br.com.techbr.fiscalanalyzer.identity.model.EmpresaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByIdAndTenantIdAndStatus(Long id, Long tenantId, EmpresaStatus status);

    boolean existsByTenantIdAndCnpj(Long tenantId, String cnpj);

    List<Empresa> findByTenantIdOrderByIdAsc(Long tenantId);

    List<Empresa> findByTenantIdAndStatusOrderByIdAsc(Long tenantId, EmpresaStatus status);
}
