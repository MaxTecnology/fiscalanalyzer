package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC190;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SpedFiscalC190Repository extends JpaRepository<SpedFiscalC190, Long> {
    void deleteByC100FileId(Long fileId);

    @Query("""
            select c.c100.id as c100Id, coalesce(sum(c.vlIcms), 0) as totalIcms
            from SpedFiscalC190 c
            where c.c100.id in :c100Ids
            group by c.c100.id
            """)
    List<C100IcmsProjection> sumIcmsByC100Ids(@Param("c100Ids") List<Long> c100Ids);

    interface C100IcmsProjection {
        Long getC100Id();
        BigDecimal getTotalIcms();
    }
}
