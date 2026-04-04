package br.com.techbr.fiscalanalyzer.item.repository;

import br.com.techbr.fiscalanalyzer.item.model.FiscalItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FiscalItemRepository extends JpaRepository<FiscalItem, Long> {

    List<FiscalItem> findByDocumentIdOrderByItemNumberAsc(Long documentId);

    @Query("""
            select i.cfop as codigo, count(i.id) as totalItens, coalesce(sum(i.totalValue), 0) as totalValor
            from FiscalItem i
            join i.document d
            where d.tenantId = :tenantId
              and d.empresaId = :empresaId
              and d.issueDate >= :issueDateFrom
              and d.issueDate <= :issueDateTo
              and i.cfop is not null
              and i.cfop <> ''
            group by i.cfop
            order by count(i.id) desc
            """)
    List<CodeStatsProjection> summarizeByCfop(@Param("tenantId") Long tenantId,
                                              @Param("empresaId") Long empresaId,
                                              @Param("issueDateFrom") LocalDate issueDateFrom,
                                              @Param("issueDateTo") LocalDate issueDateTo,
                                              Pageable pageable);

    @Query("""
            select i.ncm as codigo, count(i.id) as totalItens, coalesce(sum(i.totalValue), 0) as totalValor
            from FiscalItem i
            join i.document d
            where d.tenantId = :tenantId
              and d.empresaId = :empresaId
              and d.issueDate >= :issueDateFrom
              and d.issueDate <= :issueDateTo
              and i.ncm is not null
              and i.ncm <> ''
            group by i.ncm
            order by count(i.id) desc
            """)
    List<CodeStatsProjection> summarizeByNcm(@Param("tenantId") Long tenantId,
                                             @Param("empresaId") Long empresaId,
                                             @Param("issueDateFrom") LocalDate issueDateFrom,
                                             @Param("issueDateTo") LocalDate issueDateTo,
                                             Pageable pageable);

    interface CodeStatsProjection {
        String getCodigo();
        Long getTotalItens();
        BigDecimal getTotalValor();
    }
}
