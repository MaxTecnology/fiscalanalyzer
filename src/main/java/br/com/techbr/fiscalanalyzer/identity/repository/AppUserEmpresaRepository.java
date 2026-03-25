package br.com.techbr.fiscalanalyzer.identity.repository;

import br.com.techbr.fiscalanalyzer.identity.model.AppUserEmpresa;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserEmpresaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserEmpresaRepository extends JpaRepository<AppUserEmpresa, AppUserEmpresaId> {

    List<AppUserEmpresa> findByIdAppUserIdOrderByIdEmpresaIdAsc(Long appUserId);

    boolean existsByIdAppUserIdAndTenantIdAndIdEmpresaId(Long appUserId, Long tenantId, Long empresaId);
}
