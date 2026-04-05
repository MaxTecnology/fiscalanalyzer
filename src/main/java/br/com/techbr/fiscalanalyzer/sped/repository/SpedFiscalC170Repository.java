package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC170;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SpedFiscalC170Repository extends JpaRepository<SpedFiscalC170, Long> {
    void deleteByC100FileId(Long fileId);

    @Query("""
            select c.c100.id as c100Id, count(c.id) as totalItens
            from SpedFiscalC170 c
            where c.c100.id in :c100Ids
            group by c.c100.id
            """)
    List<C100ItemCountProjection> countByC100Ids(@Param("c100Ids") List<Long> c100Ids);

    @Query("""
            select c.cfop as codigo, count(c.id) as totalItens, coalesce(sum(c.vlItem), 0) as totalValor
            from SpedFiscalC170 c
            join c.c100 d
            where c.tenantId = :tenantId
              and c.empresaId = :empresaId
              and d.dtDoc >= :periodoInicio
              and d.dtDoc <= :periodoFim
              and c.cfop is not null
              and c.cfop <> ''
            group by c.cfop
            order by count(c.id) desc
            """)
    List<CodeStatsProjection> summarizeByCfop(@Param("tenantId") Long tenantId,
                                              @Param("empresaId") Long empresaId,
                                              @Param("periodoInicio") LocalDate periodoInicio,
                                              @Param("periodoFim") LocalDate periodoFim,
                                              Pageable pageable);

    @Query("""
            select c.cstIcms as codigo, count(c.id) as totalItens, coalesce(sum(c.vlItem), 0) as totalValor
            from SpedFiscalC170 c
            join c.c100 d
            where c.tenantId = :tenantId
              and c.empresaId = :empresaId
              and d.dtDoc >= :periodoInicio
              and d.dtDoc <= :periodoFim
              and c.cstIcms is not null
              and c.cstIcms <> ''
            group by c.cstIcms
            order by count(c.id) desc
            """)
    List<CodeStatsProjection> summarizeByCstIcms(@Param("tenantId") Long tenantId,
                                                 @Param("empresaId") Long empresaId,
                                                 @Param("periodoInicio") LocalDate periodoInicio,
                                                 @Param("periodoFim") LocalDate periodoFim,
                                                 Pageable pageable);

    interface C100ItemCountProjection {
        Long getC100Id();
        Long getTotalItens();
    }

    interface CodeStatsProjection {
        String getCodigo();
        Long getTotalItens();
        BigDecimal getTotalValor();
    }
}
