package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FiscalDocumentRegistryRepository extends JpaRepository<FiscalDocumentRegistry, Long> {
    Optional<FiscalDocumentRegistry> findByEmpresaIdAndAccessKey(Long empresaId, String accessKey);
}
