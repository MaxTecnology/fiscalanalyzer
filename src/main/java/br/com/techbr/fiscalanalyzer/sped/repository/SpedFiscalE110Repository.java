package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalE110;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SpedFiscalE110Repository extends JpaRepository<SpedFiscalE110, Long> {
    void deleteByFileId(Long fileId);

    @Query("""
            select count(e.id) as totalRegistros,
                   coalesce(sum(e.vlTotDebitos), 0) as totalDebitos,
                   coalesce(sum(e.vlTotCreditos), 0) as totalCreditos,
                   coalesce(sum(e.vlIcmsRecolher), 0) as totalIcmsRecolher
            from SpedFiscalE110 e
            join e.file f
            where e.tenantId = :tenantId
              and e.empresaId = :empresaId
              and f.status = :status
              and f.periodoFim >= :periodoInicio
              and f.periodoInicio <= :periodoFim
            """)
    E110AggregateProjection summarizeByScopeAndPeriod(@Param("tenantId") Long tenantId,
                                                      @Param("empresaId") Long empresaId,
                                                      @Param("periodoInicio") LocalDate periodoInicio,
                                                      @Param("periodoFim") LocalDate periodoFim,
                                                      @Param("status") SpedFiscalFileStatus status);

    interface E110AggregateProjection {
        Long getTotalRegistros();
        BigDecimal getTotalDebitos();
        BigDecimal getTotalCreditos();
        BigDecimal getTotalIcmsRecolher();
    }
}
