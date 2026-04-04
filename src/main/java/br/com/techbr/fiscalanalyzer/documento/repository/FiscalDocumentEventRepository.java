package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FiscalDocumentEventRepository extends JpaRepository<FiscalDocumentEvent, Long> {

    Optional<FiscalDocumentEvent> findByTenantIdAndEmpresaIdAndEventId(Long tenantId, Long empresaId, String eventId);

    List<FiscalDocumentEvent> findByTenantIdAndEmpresaIdAndAccessKeyOrderByCreatedAtAsc(
            Long tenantId,
            Long empresaId,
            String accessKey
    );

    @Query("""
            select e from FiscalDocumentEvent e
            where e.tenantId = :tenantId
              and e.empresaId = :empresaId
              and e.accessKey = :accessKey
            order by e.eventDatetime desc, e.createdAt desc
            """)
    List<FiscalDocumentEvent> findTimeline(@Param("tenantId") Long tenantId,
                                           @Param("empresaId") Long empresaId,
                                           @Param("accessKey") String accessKey);
}
