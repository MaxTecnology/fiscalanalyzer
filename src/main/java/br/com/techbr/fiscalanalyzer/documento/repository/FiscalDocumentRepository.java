package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {
    Optional<FiscalDocument> findByTenantIdAndEmpresaIdAndAccessKey(Long tenantId, Long empresaId, String accessKey);
}
