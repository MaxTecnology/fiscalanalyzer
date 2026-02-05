package br.com.techbr.fiscalanalyzer.importacao.repository;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportItemRepository extends JpaRepository<ImportItem, Long> {
    boolean existsByImportacaoIdAndXmlPath(Long importacaoId, String xmlPath);
    Optional<ImportItem> findByImportacaoIdAndXmlPath(Long importacaoId, String xmlPath);
}
