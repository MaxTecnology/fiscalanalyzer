package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportReprocessRequest;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportReprocessResponse;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportacaoReprocessService {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 5000;
    private static final Set<ImportItemStatus> REPROCESSABLE_STATUSES = EnumSet.of(
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.FALHA_PERMANENTE,
            ImportItemStatus.ERRO
    );
    private static final Set<ImportItemStatus> PROCESSED_STATUSES = EnumSet.of(
            ImportItemStatus.PARSEADO,
            ImportItemStatus.DUPLICADO,
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.CONCLUIDO,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );
    private static final Set<ImportItemStatus> ERROR_STATUSES = EnumSet.of(
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );

    private final ImportacaoRepository importacaoRepository;
    private final ImportItemRepository importItemRepository;
    private final UserAuthorizationService userAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final String bucket;

    public ImportacaoReprocessService(ImportacaoRepository importacaoRepository,
                                      ImportItemRepository importItemRepository,
                                      UserAuthorizationService userAuthorizationService,
                                      ApplicationEventPublisher eventPublisher,
                                      @Value("${storage.s3.bucket}") String bucket) {
        this.importacaoRepository = importacaoRepository;
        this.importItemRepository = importItemRepository;
        this.userAuthorizationService = userAuthorizationService;
        this.eventPublisher = eventPublisher;
        this.bucket = bucket;
    }

    @Transactional
    public ImportReprocessResponse reprocess(Long importacaoId,
                                             ImportReprocessRequest request,
                                             UserAuthContext auth) {
        if (importacaoId == null || importacaoId <= 0) {
            throw new ValidationException("id invalido");
        }

        Importacao importacao = importacaoRepository.findById(importacaoId)
                .orElseThrow(() -> new ValidationException("Importacao nao encontrada: " + importacaoId));
        userAuthorizationService.assertCanWrite(auth, importacao.getTenantId(), importacao.getEmpresaId());

        Set<ImportItemStatus> statuses = resolveStatuses(request);
        String erroCodigo = normalizeErroCodigo(request);
        int limit = resolveLimit(request);
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());

        List<ImportItem> candidates = selectCandidates(importacaoId, statuses, erroCodigo, limit);
        if (dryRun || candidates.isEmpty()) {
            return new ImportReprocessResponse(
                    importacaoId,
                    dryRun,
                    null,
                    candidates.size(),
                    0,
                    statuses.stream().map(Enum::name).sorted().toList(),
                    erroCodigo
            );
        }

        String correlationId = UUID.randomUUID().toString();
        for (ImportItem item : candidates) {
            item.setStatus(ImportItemStatus.PENDENTE_PARSE);
            item.setErroCodigo(null);
            item.setErroMensagem(null);
        }
        importItemRepository.saveAll(candidates);

        for (ImportItem item : candidates) {
            ParseXmlRequestedEvent event = toParseEvent(importacao, item, correlationId);
            eventPublisher.publishEvent(event);
        }

        updateImportacaoCounters(importacao);

        return new ImportReprocessResponse(
                importacaoId,
                false,
                correlationId,
                candidates.size(),
                candidates.size(),
                statuses.stream().map(Enum::name).sorted().toList(),
                erroCodigo
        );
    }

    private List<ImportItem> selectCandidates(Long importacaoId,
                                              Set<ImportItemStatus> statuses,
                                              String erroCodigo,
                                              int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id"));
        if (StringUtils.hasText(erroCodigo)) {
            return importItemRepository
                    .findByImportacaoIdAndStatusInAndErroCodigo(importacaoId, statuses, erroCodigo, pageRequest)
                    .getContent();
        }
        return importItemRepository.findByImportacaoIdAndStatusIn(importacaoId, statuses, pageRequest).getContent();
    }

    private Set<ImportItemStatus> resolveStatuses(ImportReprocessRequest request) {
        if (request == null || request.statuses() == null || request.statuses().isEmpty()) {
            return EnumSet.copyOf(REPROCESSABLE_STATUSES);
        }
        Set<ImportItemStatus> requested = EnumSet.copyOf(request.statuses());
        if (!REPROCESSABLE_STATUSES.containsAll(requested)) {
            throw new ValidationException("statuses permitidos para reprocesso: " +
                    REPROCESSABLE_STATUSES.stream().map(Enum::name).sorted().toList());
        }
        return requested;
    }

    private String normalizeErroCodigo(ImportReprocessRequest request) {
        if (request == null || request.erroCodigo() == null) {
            return null;
        }
        String normalized = request.erroCodigo().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int resolveLimit(ImportReprocessRequest request) {
        int limit = request != null && request.limit() != null ? request.limit() : DEFAULT_LIMIT;
        if (limit <= 0) {
            throw new ValidationException("limit deve ser maior que zero");
        }
        if (limit > MAX_LIMIT) {
            throw new ValidationException("limit maximo permitido: " + MAX_LIMIT);
        }
        return limit;
    }

    private ParseXmlRequestedEvent toParseEvent(Importacao importacao, ImportItem item, String correlationId) {
        ImportacaoSourceType sourceType = importacao.getSourceType();
        if (sourceType == null) {
            sourceType = ImportacaoSourceType.ZIP;
        }

        String objectKey;
        String zipEntryName = null;
        if (sourceType == ImportacaoSourceType.ZIP) {
            objectKey = importacao.getArquivoPath();
            zipEntryName = item.getXmlPath();
        } else {
            objectKey = StringUtils.hasText(item.getStorageObjectKey()) ? item.getStorageObjectKey() : item.getXmlPath();
        }
        if (!StringUtils.hasText(objectKey)) {
            throw new ValidationException("Nao foi possivel determinar objectKey para reprocesso do item " + item.getId());
        }

        return new ParseXmlRequestedEvent(
                importacao.getId(),
                item.getId(),
                bucket,
                objectKey,
                zipEntryName,
                item.getXmlHash(),
                correlationId
        );
    }

    private void updateImportacaoCounters(Importacao importacao) {
        Long importacaoId = importacao.getId();
        long totalItems = importItemRepository.countByImportacaoId(importacaoId);
        long processed = importItemRepository.countByImportacaoIdAndStatusIn(importacaoId, PROCESSED_STATUSES);
        long errors = importItemRepository.countByImportacaoIdAndStatusIn(importacaoId, ERROR_STATUSES);

        importacao.setTotalProcessado((int) processed);
        importacao.setTotalErros((int) errors);
        if (totalItems > 0 && processed >= totalItems) {
            importacao.setStatus(ImportacaoStatus.CONCLUIDO);
        } else {
            importacao.setStatus(ImportacaoStatus.PROCESSANDO);
        }
        importacaoRepository.save(importacao);
    }
}
