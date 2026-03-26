package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentAdditionalInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalDocumentAdditionalInfoRepository extends JpaRepository<FiscalDocumentAdditionalInfo, Long> {
    long countByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
}
