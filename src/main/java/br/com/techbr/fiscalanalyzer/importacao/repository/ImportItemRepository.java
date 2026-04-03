package br.com.techbr.fiscalanalyzer.importacao.repository;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.time.Instant;

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

    Page<ImportItem> findByImportacaoIdAndStatusIn(Long importacaoId,
                                                   Collection<ImportItemStatus> statuses,
                                                   Pageable pageable);

    Page<ImportItem> findByImportacaoIdAndStatusInAndErroCodigo(Long importacaoId,
                                                                Collection<ImportItemStatus> statuses,
                                                                String erroCodigo,
                                                                Pageable pageable);

    @Query("select i.status as status, count(i) as count from ImportItem i where i.importacao.id = :importacaoId group by i.status")
    List<StatusCountProjection> countByStatus(@Param("importacaoId") Long importacaoId);

    @Query(value = """
            select s.erro_codigo as erroCodigo,
                   s.erro_mensagem as erroMensagem,
                   s.total as totalItems,
                   i.id as sampleItemId,
                   i.xml_path as sampleXmlPath,
                   s.last_occurrence_at as lastOccurrenceAt
            from (
                select erro_codigo,
                       erro_mensagem,
                       count(*) as total,
                       min(id) as sample_item_id,
                       max(updated_at) as last_occurrence_at
                from import_item
                where importacao_id = :importacaoId
                  and status in ('FALHA_PARSE', 'FALHA_PERMANENTE', 'ERRO')
                group by erro_codigo, erro_mensagem
            ) s
            join import_item i on i.id = s.sample_item_id
            order by s.total desc, s.last_occurrence_at desc
            """, nativeQuery = true)
    List<FailureSummaryProjection> summarizeFailures(@Param("importacaoId") Long importacaoId);

    @Query("""
            select i.id as importItemId,
                   i.accessKey as accessKey,
                   i.xmlPath as xmlPath,
                   i.storageObjectKey as storageObjectKey,
                   imp.arquivoPath as arquivoPath,
                   imp.sourceType as sourceType
            from ImportItem i
            join i.importacao imp
            where imp.tenantId = :tenantId
              and imp.empresaId = :empresaId
              and i.status in :statuses
              and i.accessKey is not null
            order by i.id
            """)
    List<CoverageBackfillCandidateProjection> findCoverageBackfillCandidates(
            @Param("tenantId") Long tenantId,
            @Param("empresaId") Long empresaId,
            @Param("statuses") Set<ImportItemStatus> statuses,
            Pageable pageable
    );

    interface StatusCountProjection {
        ImportItemStatus getStatus();
        long getCount();
    }

    interface FailureSummaryProjection {
        String getErroCodigo();
        String getErroMensagem();
        long getTotalItems();
        Long getSampleItemId();
        String getSampleXmlPath();
        Instant getLastOccurrenceAt();
    }

    interface CoverageBackfillCandidateProjection {
        Long getImportItemId();
        String getAccessKey();
        String getXmlPath();
        String getStorageObjectKey();
        String getArquivoPath();
        ImportacaoSourceType getSourceType();
    }
}
