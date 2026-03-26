package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalDocumentPaymentRepository extends JpaRepository<FiscalDocumentPayment, Long> {
    long countByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
}
