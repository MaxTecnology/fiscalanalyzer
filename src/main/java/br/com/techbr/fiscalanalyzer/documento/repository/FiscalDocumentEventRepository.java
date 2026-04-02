package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FiscalDocumentEventRepository extends JpaRepository<FiscalDocumentEvent, Long> {

    Optional<FiscalDocumentEvent> findByTenantIdAndEmpresaIdAndEventId(Long tenantId, Long empresaId, String eventId);

    List<FiscalDocumentEvent> findByTenantIdAndEmpresaIdAndAccessKeyOrderByCreatedAtAsc(
            Long tenantId,
            Long empresaId,
            String accessKey
    );
}

