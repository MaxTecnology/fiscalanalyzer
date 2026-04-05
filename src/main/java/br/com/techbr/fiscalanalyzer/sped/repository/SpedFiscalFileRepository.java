package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface SpedFiscalFileRepository extends JpaRepository<SpedFiscalFile, Long> {
    Optional<SpedFiscalFile> findByTenantIdAndEmpresaIdAndHashSha256(Long tenantId, Long empresaId, String hashSha256);

    Optional<SpedFiscalFile> findByIdAndTenantIdAndEmpresaId(Long id, Long tenantId, Long empresaId);

    Page<SpedFiscalFile> findByTenantIdAndEmpresaId(Long tenantId, Long empresaId, Pageable pageable);

    Page<SpedFiscalFile> findByTenantIdAndEmpresaIdAndStatus(
            Long tenantId,
            Long empresaId,
            SpedFiscalFileStatus status,
            Pageable pageable
    );

    @Query(value = """
            select status as status, count(*) as quantidade
            from sped_fiscal_file
            where tenant_id = :tenantId
              and empresa_id = :empresaId
              and (:periodoInicio is null or periodo_inicio >= :periodoInicio)
              and (:periodoFim is null or periodo_fim <= :periodoFim)
            group by status
            """, nativeQuery = true)
    List<SpedStatusCountProjection> countByStatus(
            @Param("tenantId") Long tenantId,
            @Param("empresaId") Long empresaId,
            @Param("periodoInicio") LocalDate periodoInicio,
            @Param("periodoFim") LocalDate periodoFim
    );

    @Query("""
            select count(f)
            from SpedFiscalFile f
            where f.tenantId = :tenantId
              and f.empresaId = :empresaId
              and f.status = :status
              and f.periodoFim >= :periodoInicio
              and f.periodoInicio <= :periodoFim
            """)
    long countByScopeAndPeriodAndStatus(@Param("tenantId") Long tenantId,
                                        @Param("empresaId") Long empresaId,
                                        @Param("periodoInicio") LocalDate periodoInicio,
                                        @Param("periodoFim") LocalDate periodoFim,
                                        @Param("status") SpedFiscalFileStatus status);

    interface SpedStatusCountProjection {
        String getStatus();
        Long getQuantidade();
    }
}
