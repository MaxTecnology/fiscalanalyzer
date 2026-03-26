package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentDuplicate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalDocumentDuplicateRepository extends JpaRepository<FiscalDocumentDuplicate, Long> {
    void deleteByDocumentId(Long documentId);
}
