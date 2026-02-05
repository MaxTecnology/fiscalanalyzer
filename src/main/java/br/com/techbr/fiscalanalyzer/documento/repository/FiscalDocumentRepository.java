package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {}
