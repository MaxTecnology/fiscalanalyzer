package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalE111;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SpedFiscalE111Repository extends JpaRepository<SpedFiscalE111, Long> {
    void deleteByE110FileId(Long fileId);

    @Query("""
            select coalesce(sum(e.vlAjApur), 0)
            from SpedFiscalE111 e
            join e.e110 ap
            join ap.file f
            where e.tenantId = :tenantId
              and e.empresaId = :empresaId
              and f.status = :status
              and f.periodoFim >= :periodoInicio
              and f.periodoInicio <= :periodoFim
            """)
    BigDecimal sumAdjustmentsByScopeAndPeriod(@Param("tenantId") Long tenantId,
                                              @Param("empresaId") Long empresaId,
                                              @Param("periodoInicio") LocalDate periodoInicio,
                                              @Param("periodoFim") LocalDate periodoFim,
                                              @Param("status") SpedFiscalFileStatus status);

    @Query("""
            select coalesce(e.codAjApur, 'SEM_CODIGO') as codigo,
                   count(e.id) as totalRegistros,
                   coalesce(sum(e.vlAjApur), 0) as totalValor
            from SpedFiscalE111 e
            join e.e110 ap
            join ap.file f
            where e.tenantId = :tenantId
              and e.empresaId = :empresaId
              and f.status = :status
              and f.periodoFim >= :periodoInicio
              and f.periodoInicio <= :periodoFim
            group by coalesce(e.codAjApur, 'SEM_CODIGO')
            order by count(e.id) desc
            """)
    List<E111CodeProjection> summarizeTopAdjustmentsByScopeAndPeriod(@Param("tenantId") Long tenantId,
                                                                      @Param("empresaId") Long empresaId,
                                                                      @Param("periodoInicio") LocalDate periodoInicio,
                                                                      @Param("periodoFim") LocalDate periodoFim,
                                                                      @Param("status") SpedFiscalFileStatus status,
                                                                      Pageable pageable);

    interface E111CodeProjection {
        String getCodigo();
        Long getTotalRegistros();
        BigDecimal getTotalValor();
    }
}
