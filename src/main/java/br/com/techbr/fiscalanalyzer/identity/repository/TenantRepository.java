package br.com.techbr.fiscalanalyzer.identity.repository;

import br.com.techbr.fiscalanalyzer.identity.model.Tenant;
import br.com.techbr.fiscalanalyzer.identity.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByIdAndStatus(Long id, TenantStatus status);

    List<Tenant> findAllByOrderByIdAsc();

    List<Tenant> findByStatusOrderByIdAsc(TenantStatus status);
}
