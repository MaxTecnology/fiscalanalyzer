package br.com.techbr.fiscalanalyzer.importacao.repository;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface ImportItemRepository extends JpaRepository<ImportItem, Long> {
    boolean existsByImportacaoIdAndXmlPath(Long importacaoId, String xmlPath);
    Optional<ImportItem> findByImportacaoIdAndXmlPath(Long importacaoId, String xmlPath);
    long countByImportacaoIdAndStatusIn(Long importacaoId, Collection<ImportItemStatus> statuses);

    long countByImportacaoId(Long importacaoId);

    Page<ImportItem> findByImportacaoId(Long importacaoId, Pageable pageable);

    Page<ImportItem> findByImportacaoIdAndStatus(Long importacaoId, ImportItemStatus status, Pageable pageable);

    @Query("select i.status as status, count(i) as count from ImportItem i where i.importacao.id = :importacaoId group by i.status")
    List<StatusCountProjection> countByStatus(@Param("importacaoId") Long importacaoId);

    interface StatusCountProjection {
        ImportItemStatus getStatus();
        long getCount();
    }
}
