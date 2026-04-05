package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedApuracaoSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedCodeStatsResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedE111AdjustmentSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedFiscalFileDetailResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedFiscalFileSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationDivergenceResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationItemResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedStatusCountResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.event.ParseSpedRequestedEvent;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC100;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.model.SpedReconciliationStatus;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC170Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC190Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC100Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE110Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE111Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SpedFiscalReadService {

    private final SpedFiscalFileRepository repository;
    private final SpedFiscalC100Repository spedFiscalC100Repository;
    private final SpedFiscalC170Repository spedFiscalC170Repository;
    private final SpedFiscalC190Repository spedFiscalC190Repository;
    private final SpedFiscalE110Repository spedFiscalE110Repository;
    private final SpedFiscalE111Repository spedFiscalE111Repository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalItemRepository fiscalItemRepository;
    private final UserAuthorizationService userAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final String bucket;

    public SpedFiscalReadService(SpedFiscalFileRepository repository,
                                 SpedFiscalC100Repository spedFiscalC100Repository,
                                 SpedFiscalC170Repository spedFiscalC170Repository,
                                 SpedFiscalC190Repository spedFiscalC190Repository,
                                 SpedFiscalE110Repository spedFiscalE110Repository,
                                 SpedFiscalE111Repository spedFiscalE111Repository,
                                 FiscalDocumentRepository fiscalDocumentRepository,
                                 FiscalItemRepository fiscalItemRepository,
                                 UserAuthorizationService userAuthorizationService,
                                 ApplicationEventPublisher eventPublisher,
                                 @Value("${storage.s3.bucket}") String bucket) {
        this.repository = repository;
        this.spedFiscalC100Repository = spedFiscalC100Repository;
        this.spedFiscalC170Repository = spedFiscalC170Repository;
        this.spedFiscalC190Repository = spedFiscalC190Repository;
        this.spedFiscalE110Repository = spedFiscalE110Repository;
        this.spedFiscalE111Repository = spedFiscalE111Repository;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.fiscalItemRepository = fiscalItemRepository;
        this.userAuthorizationService = userAuthorizationService;
        this.eventPublisher = eventPublisher;
        this.bucket = bucket;
    }

    @Transactional(readOnly = true)
    public Page<SpedFiscalFileSummaryResponse> list(Long tenantId,
                                                    Long empresaId,
                                                    SpedFiscalFileStatus status,
                                                    Pageable pageable,
                                                    UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        Page<SpedFiscalFile> page = status == null
                ? repository.findByTenantIdAndEmpresaId(tenantId, empresaId, pageable)
                : repository.findByTenantIdAndEmpresaIdAndStatus(tenantId, empresaId, status, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public SpedFiscalFileDetailResponse detail(Long id, Long tenantId, Long empresaId, UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        SpedFiscalFile file = repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)
                .orElseThrow(() -> new UnprocessableEntityException("Arquivo SPED nao encontrado"));
        return toDetail(file);
    }

    @Transactional(readOnly = true)
    public SpedSummaryResponse summary(Long tenantId,
                                       Long empresaId,
                                       LocalDate periodoInicio,
                                       LocalDate periodoFim,
                                       UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        List<SpedStatusCountResponse> counters = new ArrayList<>();
        long total = 0L;
        for (var row : repository.countByStatus(tenantId, empresaId, periodoInicio, periodoFim)) {
            SpedFiscalFileStatus status;
            try {
                status = SpedFiscalFileStatus.valueOf(row.getStatus());
            } catch (Exception ex) {
                continue;
            }
            long quantidade = row.getQuantidade() != null ? row.getQuantidade() : 0L;
            counters.add(new SpedStatusCountResponse(status, quantidade));
            total += quantidade;
        }
        return new SpedSummaryResponse(tenantId, empresaId, periodoInicio, periodoFim, total, counters);
    }

    @Transactional
    public SpedFiscalFileDetailResponse reprocess(Long id, Long tenantId, Long empresaId, UserAuthContext auth) {
        userAuthorizationService.assertCanWrite(auth, tenantId, empresaId);
        SpedFiscalFile file = repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)
                .orElseThrow(() -> new UnprocessableEntityException("Arquivo SPED nao encontrado"));

        if (file.getStatus() == SpedFiscalFileStatus.PROCESSANDO) {
            throw new ValidationException("Arquivo SPED em processamento");
        }

        file.setStatus(SpedFiscalFileStatus.PENDENTE_PARSE);
        file.setErroCodigo(null);
        file.setErroMensagem(null);
        repository.save(file);

        eventPublisher.publishEvent(new ParseSpedRequestedEvent(
                file.getId(),
                bucket,
                file.getStorageObjectKey(),
                file.getZipEntryName(),
                file.getHashSha256(),
                UUID.randomUUID().toString()
        ));
        return toDetail(file);
    }

    @Transactional(readOnly = true)
    public Page<SpedReconciliationItemResponse> reconciliation(Long fileId,
                                                               Long tenantId,
                                                               Long empresaId,
                                                               Pageable pageable,
                                                               UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        repository.findByIdAndTenantIdAndEmpresaId(fileId, tenantId, empresaId)
                .orElseThrow(() -> new UnprocessableEntityException("Arquivo SPED nao encontrado"));

        Page<SpedFiscalC100> page = spedFiscalC100Repository.findByFileIdOrderByLinhaOrigemAsc(fileId, pageable);
        List<SpedReconciliationItemResponse> rows = buildRowsForBatch(tenantId, empresaId, page.getContent()).stream()
                .map(this::toReconciliationItemResponse)
                .toList();

        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<SpedReconciliationDivergenceResponse> reconciliationDivergences(Long tenantId,
                                                                                 Long empresaId,
                                                                                 LocalDate periodoInicio,
                                                                                 LocalDate periodoFim,
                                                                                 SpedReconciliationStatus status,
                                                                                 String accessKey,
                                                                                 Pageable pageable,
                                                                                 UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        validateDateRange(periodoInicio, periodoFim);
        String normalizedAccessKey = normalizeAccessKey(accessKey);

        List<ComputedReconciliationRow> filteredRows = new ArrayList<>();
        int pageNumber = 0;
        final int batchSize = 2000;
        Page<SpedFiscalC100> batch;
        do {
            batch = spedFiscalC100Repository.findByScopeAndPeriod(
                    tenantId,
                    empresaId,
                    periodoInicio,
                    periodoFim,
                    SpedFiscalFileStatus.PROCESSADO,
                    PageRequest.of(pageNumber, batchSize)
            );
            if (batch.isEmpty()) {
                break;
            }
            for (ComputedReconciliationRow row : buildRowsForBatch(tenantId, empresaId, batch.getContent())) {
                if (!matchesStatusFilter(row.status, status)) {
                    continue;
                }
                if (normalizedAccessKey != null && !normalizedAccessKey.equals(row.accessKey)) {
                    continue;
                }
                filteredRows.add(row);
            }
            pageNumber++;
        } while (batch.hasNext());

        filteredRows.sort(Comparator
                .comparing((ComputedReconciliationRow row) -> row.issueDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing((ComputedReconciliationRow row) -> row.spedFileId, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing((ComputedReconciliationRow row) -> row.spedC100Id, Comparator.nullsLast(Comparator.reverseOrder())));

        long total = filteredRows.size();
        int fromIndex = Math.toIntExact(pageable.getOffset());
        if (fromIndex >= filteredRows.size()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filteredRows.size());
        List<SpedReconciliationDivergenceResponse> content = filteredRows.subList(fromIndex, toIndex).stream()
                .map(this::toDivergenceResponse)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public SpedReconciliationSummaryResponse reconciliationSummary(Long tenantId,
                                                                   Long empresaId,
                                                                   LocalDate periodoInicio,
                                                                   LocalDate periodoFim,
                                                                   Integer limit,
                                                                   UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        validateDateRange(periodoInicio, periodoFim);
        int safeLimit = clampLimit(limit);

        long totalArquivosProcessados = repository.countByScopeAndPeriodAndStatus(
                tenantId,
                empresaId,
                periodoInicio,
                periodoFim,
                SpedFiscalFileStatus.PROCESSADO
        );

        ReconciliationCounters counters = new ReconciliationCounters();
        int pageNumber = 0;
        final int batchSize = 2000;
        Page<SpedFiscalC100> page;
        do {
            page = spedFiscalC100Repository.findByScopeAndPeriod(
                    tenantId,
                    empresaId,
                    periodoInicio,
                    periodoFim,
                    SpedFiscalFileStatus.PROCESSADO,
                    PageRequest.of(pageNumber, batchSize)
            );
            if (page.isEmpty()) {
                break;
            }
            accumulateCounters(tenantId, empresaId, page.getContent(), counters);
            pageNumber++;
        } while (page.hasNext());

        List<SpedCodeStatsResponse> cfopSpedTop = spedFiscalC170Repository
                .summarizeByCfop(tenantId, empresaId, periodoInicio, periodoFim, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toSpedCodeStats)
                .toList();

        List<SpedCodeStatsResponse> cstSpedTop = spedFiscalC170Repository
                .summarizeByCstIcms(tenantId, empresaId, periodoInicio, periodoFim, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toSpedCodeStats)
                .toList();

        List<SpedCodeStatsResponse> cfopXmlTop = fiscalItemRepository
                .summarizeByCfop(tenantId, empresaId, periodoInicio, periodoFim, PageRequest.of(0, safeLimit))
                .stream()
                .map(row -> new SpedCodeStatsResponse(row.getCodigo(), safeLong(row.getTotalItens()), defaultZero(row.getTotalValor())))
                .toList();

        List<SpedCodeStatsResponse> ncmXmlTop = fiscalItemRepository
                .summarizeByNcm(tenantId, empresaId, periodoInicio, periodoFim, PageRequest.of(0, safeLimit))
                .stream()
                .map(row -> new SpedCodeStatsResponse(row.getCodigo(), safeLong(row.getTotalItens()), defaultZero(row.getTotalValor())))
                .toList();

        SpedFiscalE110Repository.E110AggregateProjection e110Projection = spedFiscalE110Repository
                .summarizeByScopeAndPeriod(tenantId, empresaId, periodoInicio, periodoFim, SpedFiscalFileStatus.PROCESSADO);

        BigDecimal totalAjustes = defaultZero(spedFiscalE111Repository
                .sumAdjustmentsByScopeAndPeriod(tenantId, empresaId, periodoInicio, periodoFim, SpedFiscalFileStatus.PROCESSADO));

        List<SpedE111AdjustmentSummaryResponse> topAjustes = spedFiscalE111Repository
                .summarizeTopAdjustmentsByScopeAndPeriod(
                        tenantId,
                        empresaId,
                        periodoInicio,
                        periodoFim,
                        SpedFiscalFileStatus.PROCESSADO,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(row -> new SpedE111AdjustmentSummaryResponse(
                        row.getCodigo(),
                        safeLong(row.getTotalRegistros()),
                        defaultZero(row.getTotalValor())
                ))
                .toList();

        SpedApuracaoSummaryResponse apuracao = new SpedApuracaoSummaryResponse(
                e110Projection != null ? safeLong(e110Projection.getTotalRegistros()) : 0L,
                e110Projection != null ? defaultZero(e110Projection.getTotalDebitos()) : BigDecimal.ZERO,
                e110Projection != null ? defaultZero(e110Projection.getTotalCreditos()) : BigDecimal.ZERO,
                e110Projection != null ? defaultZero(e110Projection.getTotalIcmsRecolher()) : BigDecimal.ZERO,
                totalAjustes,
                topAjustes
        );

        return new SpedReconciliationSummaryResponse(
                tenantId,
                empresaId,
                periodoInicio,
                periodoFim,
                totalArquivosProcessados,
                counters.totalDocumentos,
                counters.totalOk,
                counters.totalFaltanteXml,
                counters.totalValorDivergente,
                counters.totalItemDivergente,
                counters.totalImpostoDivergente,
                counters.totalSemChave,
                cfopSpedTop,
                cstSpedTop,
                cfopXmlTop,
                ncmXmlTop,
                apuracao
        );
    }

    private SpedFiscalFileSummaryResponse toSummary(SpedFiscalFile file) {
        return new SpedFiscalFileSummaryResponse(
                file.getId(),
                file.getStatus(),
                file.getSourceType(),
                file.getOriginalFileName(),
                file.getHashSha256(),
                file.getLayoutVersion(),
                file.getPeriodoInicio(),
                file.getPeriodoFim(),
                file.getErroCodigo(),
                file.getErroMensagem(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    private SpedFiscalFileDetailResponse toDetail(SpedFiscalFile file) {
        return new SpedFiscalFileDetailResponse(
                file.getId(),
                file.getTenantId(),
                file.getEmpresaId(),
                file.getStatus(),
                file.getSourceType(),
                file.getOriginalFileName(),
                file.getStorageObjectKey(),
                file.getZipEntryName(),
                file.getHashSha256(),
                file.getContentSize(),
                file.getContentType(),
                file.getLayoutVersion(),
                file.getPeriodoInicio(),
                file.getPeriodoFim(),
                file.getLineCountDeclared(),
                file.getLineCountProcessed(),
                file.getErroCodigo(),
                file.getErroMensagem(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    private void accumulateCounters(Long tenantId,
                                    Long empresaId,
                                    List<SpedFiscalC100> c100Batch,
                                    ReconciliationCounters counters) {
        for (ComputedReconciliationRow row : buildRowsForBatch(tenantId, empresaId, c100Batch)) {
            counters.totalDocumentos++;
            counters.increment(row.status);
        }
    }

    private List<ComputedReconciliationRow> buildRowsForBatch(Long tenantId,
                                                              Long empresaId,
                                                              List<SpedFiscalC100> c100Batch) {
        if (c100Batch == null || c100Batch.isEmpty()) {
            return List.of();
        }
        List<String> accessKeys = c100Batch.stream()
                .map(SpedFiscalC100::getChvNfe)
                .filter(chv -> chv != null && !chv.isBlank())
                .distinct()
                .toList();
        List<Long> c100Ids = c100Batch.stream()
                .map(SpedFiscalC100::getId)
                .filter(id -> id != null && id > 0)
                .toList();

        Map<Long, Long> spedItemCountByC100Id = loadSpedItemCountByC100Id(c100Ids);
        Map<Long, BigDecimal> spedIcmsByC100Id = loadSpedIcmsByC100Id(c100Ids);
        Map<String, FiscalDocument> docsByAccessKey = loadDocumentsByAccessKey(tenantId, empresaId, accessKeys);
        Map<Long, XmlItemStats> xmlItemStatsByDocumentId = loadXmlItemStatsByDocumentId(docsByAccessKey.values());

        List<ComputedReconciliationRow> rows = new ArrayList<>(c100Batch.size());
        for (SpedFiscalC100 c100 : c100Batch) {
            FiscalDocument document = docsByAccessKey.get(c100.getChvNfe());
            XmlItemStats xmlItemStats = document != null ? xmlItemStatsByDocumentId.get(document.getId()) : null;
            Long totalItemsSped = spedItemCountByC100Id.get(c100.getId());
            Long totalItemsXml = xmlItemStats != null ? xmlItemStats.totalItems : null;
            BigDecimal totalIcmsSped = firstNonNull(c100.getVlIcms(), spedIcmsByC100Id.get(c100.getId()));
            BigDecimal totalIcmsXml = firstNonNull(
                    document != null ? document.getTotalIcms() : null,
                    xmlItemStats != null ? xmlItemStats.totalIcms : null
            );
            BigDecimal totalPisXml = firstNonNull(
                    document != null ? document.getTotalPis() : null,
                    xmlItemStats != null ? xmlItemStats.totalPis : null
            );
            BigDecimal totalCofinsXml = firstNonNull(
                    document != null ? document.getTotalCofins() : null,
                    xmlItemStats != null ? xmlItemStats.totalCofins : null
            );
            BigDecimal totalXml = document != null ? document.getTotalAmount() : null;
            String divergenceReason = buildDivergenceReason(
                    c100,
                    document,
                    totalItemsSped,
                    totalItemsXml,
                    totalIcmsSped,
                    totalIcmsXml,
                    totalPisXml,
                    totalCofinsXml
            );
            SpedReconciliationStatus status = calculateStatus(
                    c100,
                    totalXml,
                    totalItemsSped,
                    totalItemsXml,
                    totalIcmsSped,
                    totalIcmsXml,
                    totalPisXml,
                    totalCofinsXml
            );
            rows.add(new ComputedReconciliationRow(
                    c100.getFile() != null ? c100.getFile().getId() : null,
                    c100.getId(),
                    c100.getLinhaOrigem(),
                    c100.getChvNfe(),
                    c100.getDtDoc(),
                    c100.getCodMod(),
                    c100.getNumDoc(),
                    c100.getSerie(),
                    c100.getVlDoc(),
                    totalXml,
                    totalIcmsSped,
                    totalIcmsXml,
                    c100.getVlPis(),
                    totalPisXml,
                    c100.getVlCofins(),
                    totalCofinsXml,
                    totalItemsSped,
                    totalItemsXml,
                    divergenceReason,
                    status
            ));
        }
        return rows;
    }

    private SpedReconciliationItemResponse toReconciliationItemResponse(ComputedReconciliationRow row) {
        return new SpedReconciliationItemResponse(
                row.spedC100Id,
                row.linhaOrigem,
                row.accessKey,
                row.issueDate,
                row.model,
                row.numeroDocumento,
                row.serie,
                row.totalSped,
                row.totalXml,
                row.totalIcmsSped,
                row.totalIcmsXml,
                row.totalPisSped,
                row.totalPisXml,
                row.totalCofinsSped,
                row.totalCofinsXml,
                row.totalItemsSped,
                row.totalItemsXml,
                row.divergenceReason,
                row.status
        );
    }

    private SpedReconciliationDivergenceResponse toDivergenceResponse(ComputedReconciliationRow row) {
        return new SpedReconciliationDivergenceResponse(
                row.spedFileId,
                row.spedC100Id,
                row.linhaOrigem,
                row.accessKey,
                row.issueDate,
                row.model,
                row.numeroDocumento,
                row.serie,
                row.totalSped,
                row.totalXml,
                row.totalIcmsSped,
                row.totalIcmsXml,
                row.totalPisSped,
                row.totalPisXml,
                row.totalCofinsSped,
                row.totalCofinsXml,
                row.totalItemsSped,
                row.totalItemsXml,
                row.divergenceReason,
                row.status
        );
    }

    private SpedReconciliationStatus calculateStatus(SpedFiscalC100 c100,
                                                     BigDecimal totalXml,
                                                     Long totalItemsSped,
                                                     Long totalItemsXml,
                                                     BigDecimal totalIcmsSped,
                                                     BigDecimal totalIcmsXml,
                                                     BigDecimal totalPisXml,
                                                     BigDecimal totalCofinsXml) {
        if (c100.getChvNfe() == null || c100.getChvNfe().isBlank()) {
            return SpedReconciliationStatus.SEM_CHAVE;
        }
        if (totalXml == null) {
            return SpedReconciliationStatus.FALTANTE_XML;
        }
        if (isDifferent(c100.getVlDoc(), totalXml)) {
            return SpedReconciliationStatus.VALOR_DIVERGENTE;
        }
        if (totalItemsSped != null && totalItemsXml != null && !totalItemsSped.equals(totalItemsXml)) {
            return SpedReconciliationStatus.ITEM_DIVERGENTE;
        }
        if (isDifferent(totalIcmsSped, totalIcmsXml)
                || isDifferent(c100.getVlPis(), totalPisXml)
                || isDifferent(c100.getVlCofins(), totalCofinsXml)) {
            return SpedReconciliationStatus.IMPOSTO_DIVERGENTE;
        }
        return SpedReconciliationStatus.OK;
    }

    private Map<String, FiscalDocument> loadDocumentsByAccessKey(Long tenantId, Long empresaId, List<String> accessKeys) {
        if (accessKeys == null || accessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        return fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKeyIn(tenantId, empresaId, accessKeys).stream()
                .collect(Collectors.toMap(FiscalDocument::getAccessKey, d -> d, (left, right) -> left, HashMap::new));
    }

    private Map<Long, Long> loadSpedItemCountByC100Id(List<Long> c100Ids) {
        if (c100Ids == null || c100Ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return spedFiscalC170Repository.countByC100Ids(c100Ids).stream()
                .collect(Collectors.toMap(
                        SpedFiscalC170Repository.C100ItemCountProjection::getC100Id,
                        row -> row.getTotalItens() != null ? row.getTotalItens() : 0L
                ));
    }

    private Map<Long, BigDecimal> loadSpedIcmsByC100Id(List<Long> c100Ids) {
        if (c100Ids == null || c100Ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return spedFiscalC190Repository.sumIcmsByC100Ids(c100Ids).stream()
                .collect(Collectors.toMap(
                        SpedFiscalC190Repository.C100IcmsProjection::getC100Id,
                        row -> defaultZero(row.getTotalIcms())
                ));
    }

    private Map<Long, XmlItemStats> loadXmlItemStatsByDocumentId(Iterable<FiscalDocument> documents) {
        List<Long> documentIds = new ArrayList<>();
        for (FiscalDocument document : documents) {
            if (document != null && document.getId() != null) {
                documentIds.add(document.getId());
            }
        }
        if (documentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fiscalItemRepository.summarizeByDocumentIds(documentIds).stream()
                .collect(Collectors.toMap(
                        FiscalItemRepository.DocumentItemStatsProjection::getDocumentId,
                        row -> new XmlItemStats(
                                row.getTotalItens() != null ? row.getTotalItens() : 0L,
                                defaultZero(row.getTotalIcms()),
                                defaultZero(row.getTotalPis()),
                                defaultZero(row.getTotalCofins())
                        )
                ));
    }

    private SpedCodeStatsResponse toSpedCodeStats(SpedFiscalC170Repository.CodeStatsProjection projection) {
        return new SpedCodeStatsResponse(
                projection.getCodigo(),
                safeLong(projection.getTotalItens()),
                defaultZero(projection.getTotalValor())
        );
    }

    private void validateDateRange(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new ValidationException("periodoInicio e periodoFim sao obrigatorios");
        }
        if (inicio.isAfter(fim)) {
            throw new ValidationException("periodoInicio nao pode ser maior que periodoFim");
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 100);
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private String normalizeAccessKey(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return null;
        }
        String normalized = accessKey.replaceAll("\\D", "");
        if (normalized.length() != 44) {
            throw new ValidationException("accessKey invalida");
        }
        return normalized;
    }

    private boolean matchesStatusFilter(SpedReconciliationStatus current, SpedReconciliationStatus requested) {
        if (requested != null) {
            return current == requested;
        }
        return current != SpedReconciliationStatus.OK;
    }

    private String buildDivergenceReason(SpedFiscalC100 c100,
                                         FiscalDocument document,
                                         Long totalItemsSped,
                                         Long totalItemsXml,
                                         BigDecimal totalIcmsSped,
                                         BigDecimal totalIcmsXml,
                                         BigDecimal totalPisXml,
                                         BigDecimal totalCofinsXml) {
        if (c100.getChvNfe() == null || c100.getChvNfe().isBlank()) {
            return "sem_chave";
        }
        if (document == null) {
            return "xml_nao_encontrado";
        }
        if (isDifferent(c100.getVlDoc(), document.getTotalAmount())) {
            return "vl_doc_divergente";
        }
        if (totalItemsSped != null && totalItemsXml != null && !totalItemsSped.equals(totalItemsXml)) {
            return "qtd_itens_divergente";
        }
        List<String> taxDiffs = new ArrayList<>(3);
        if (isDifferent(totalIcmsSped, totalIcmsXml)) {
            taxDiffs.add("icms");
        }
        if (isDifferent(c100.getVlPis(), totalPisXml)) {
            taxDiffs.add("pis");
        }
        if (isDifferent(c100.getVlCofins(), totalCofinsXml)) {
            taxDiffs.add("cofins");
        }
        if (!taxDiffs.isEmpty()) {
            taxDiffs.sort(Comparator.naturalOrder());
            return "imposto_divergente:" + String.join(",", taxDiffs);
        }
        return null;
    }

    private boolean isDifferent(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) != 0;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    private static final class ComputedReconciliationRow {
        private final Long spedFileId;
        private final Long spedC100Id;
        private final Integer linhaOrigem;
        private final String accessKey;
        private final LocalDate issueDate;
        private final String model;
        private final String numeroDocumento;
        private final String serie;
        private final BigDecimal totalSped;
        private final BigDecimal totalXml;
        private final BigDecimal totalIcmsSped;
        private final BigDecimal totalIcmsXml;
        private final BigDecimal totalPisSped;
        private final BigDecimal totalPisXml;
        private final BigDecimal totalCofinsSped;
        private final BigDecimal totalCofinsXml;
        private final Long totalItemsSped;
        private final Long totalItemsXml;
        private final String divergenceReason;
        private final SpedReconciliationStatus status;

        private ComputedReconciliationRow(Long spedFileId,
                                          Long spedC100Id,
                                          Integer linhaOrigem,
                                          String accessKey,
                                          LocalDate issueDate,
                                          String model,
                                          String numeroDocumento,
                                          String serie,
                                          BigDecimal totalSped,
                                          BigDecimal totalXml,
                                          BigDecimal totalIcmsSped,
                                          BigDecimal totalIcmsXml,
                                          BigDecimal totalPisSped,
                                          BigDecimal totalPisXml,
                                          BigDecimal totalCofinsSped,
                                          BigDecimal totalCofinsXml,
                                          Long totalItemsSped,
                                          Long totalItemsXml,
                                          String divergenceReason,
                                          SpedReconciliationStatus status) {
            this.spedFileId = spedFileId;
            this.spedC100Id = spedC100Id;
            this.linhaOrigem = linhaOrigem;
            this.accessKey = accessKey;
            this.issueDate = issueDate;
            this.model = model;
            this.numeroDocumento = numeroDocumento;
            this.serie = serie;
            this.totalSped = totalSped;
            this.totalXml = totalXml;
            this.totalIcmsSped = totalIcmsSped;
            this.totalIcmsXml = totalIcmsXml;
            this.totalPisSped = totalPisSped;
            this.totalPisXml = totalPisXml;
            this.totalCofinsSped = totalCofinsSped;
            this.totalCofinsXml = totalCofinsXml;
            this.totalItemsSped = totalItemsSped;
            this.totalItemsXml = totalItemsXml;
            this.divergenceReason = divergenceReason;
            this.status = status;
        }
    }

    private static final class ReconciliationCounters {
        private long totalDocumentos;
        private long totalOk;
        private long totalFaltanteXml;
        private long totalValorDivergente;
        private long totalItemDivergente;
        private long totalImpostoDivergente;
        private long totalSemChave;

        private void increment(SpedReconciliationStatus status) {
            if (status == null) {
                return;
            }
            switch (status) {
                case OK -> totalOk++;
                case FALTANTE_XML -> totalFaltanteXml++;
                case VALOR_DIVERGENTE -> totalValorDivergente++;
                case ITEM_DIVERGENTE -> totalItemDivergente++;
                case IMPOSTO_DIVERGENTE -> totalImpostoDivergente++;
                case SEM_CHAVE -> totalSemChave++;
            }
        }
    }

    private static final class XmlItemStats {
        private final Long totalItems;
        private final BigDecimal totalIcms;
        private final BigDecimal totalPis;
        private final BigDecimal totalCofins;

        private XmlItemStats(Long totalItems, BigDecimal totalIcms, BigDecimal totalPis, BigDecimal totalCofins) {
            this.totalItems = totalItems;
            this.totalIcms = totalIcms;
            this.totalPis = totalPis;
            this.totalCofins = totalCofins;
        }
    }
}
