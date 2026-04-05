package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC100;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SpedFiscalC100Repository extends JpaRepository<SpedFiscalC100, Long> {

    void deleteByFileId(Long fileId);

    long countByFileId(Long fileId);

    Page<SpedFiscalC100> findByFileIdOrderByLinhaOrigemAsc(Long fileId, Pageable pageable);

    List<SpedFiscalC100> findByFileId(Long fileId);

    @Query("""
            select c
            from SpedFiscalC100 c
            join c.file f
            where c.tenantId = :tenantId
              and c.empresaId = :empresaId
              and f.status = :status
              and f.periodoFim >= :periodoInicio
              and f.periodoInicio <= :periodoFim
            order by c.id asc
            """)
    Page<SpedFiscalC100> findByScopeAndPeriod(@Param("tenantId") Long tenantId,
                                              @Param("empresaId") Long empresaId,
                                              @Param("periodoInicio") LocalDate periodoInicio,
                                              @Param("periodoFim") LocalDate periodoFim,
                                              @Param("status") SpedFiscalFileStatus status,
                                              Pageable pageable);
}
