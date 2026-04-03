package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportFailureSummaryResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoDetailResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportItemResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportItemStatusCountResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoSummaryResponse;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ImportacaoReadService {

    private final ImportacaoRepository importacaoRepository;
    private final ImportItemRepository importItemRepository;
    private final UserAuthorizationService userAuthorizationService;

    public ImportacaoReadService(ImportacaoRepository importacaoRepository,
                                 ImportItemRepository importItemRepository,
                                 UserAuthorizationService userAuthorizationService) {
        this.importacaoRepository = importacaoRepository;
        this.importItemRepository = importItemRepository;
        this.userAuthorizationService = userAuthorizationService;
    }

    @Transactional(readOnly = true)
    public Page<ImportacaoSummaryResponse> listImports(Long tenantId,
                                                       Long empresaId,
                                                       ImportacaoStatus status,
                                                       Pageable pageable,
                                                       UserAuthContext auth) {
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        Page<Importacao> page;
        if (status == null) {
            page = importacaoRepository.findByTenantIdAndEmpresaId(tenantId, empresaId, pageable);
        } else {
            page = importacaoRepository.findByTenantIdAndEmpresaIdAndStatus(tenantId, empresaId, status, pageable);
        }
        return page.map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public ImportacaoDetailResponse getDetail(Long id, UserAuthContext auth) {
        Importacao imp = loadAndAuthorize(id, auth);

        long totalItems = importItemRepository.countByImportacaoId(id);
        List<ImportItemStatusCountResponse> statusCounts = importItemRepository.countByStatus(id).stream()
                .map(p -> new ImportItemStatusCountResponse(p.getStatus().name(), p.getCount()))
                .toList();

        return new ImportacaoDetailResponse(
                imp.getId(),
                imp.getStatus().name(),
                imp.getCreatedAt(),
                imp.getUpdatedAt(),
                imp.getArquivoNome(),
                imp.getArquivoTamanho(),
                imp.getArquivoContentType(),
                imp.getArquivoHash(),
                imp.getErroCodigo(),
                imp.getErroMensagem(),
                totalItems,
                statusCounts
        );
    }

    @Transactional(readOnly = true)
    public Page<ImportItemResponse> listItems(Long importacaoId,
                                              ImportItemStatus status,
                                              Pageable pageable,
                                              UserAuthContext auth) {
        loadAndAuthorize(importacaoId, auth);
        Page<ImportItem> page;
        if (status == null) {
            page = importItemRepository.findByImportacaoId(importacaoId, pageable);
        } else {
            page = importItemRepository.findByImportacaoIdAndStatus(importacaoId, status, pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ImportFailureSummaryResponse> summarizeFailures(Long importacaoId, UserAuthContext auth) {
        loadAndAuthorize(importacaoId, auth);
        return importItemRepository.summarizeFailures(importacaoId).stream()
                .map(this::toFailureSummary)
                .toList();
    }

    private ImportItemResponse toResponse(ImportItem item) {
        return new ImportItemResponse(
                item.getId(),
                item.getStatus().name(),
                item.getXmlPath(),
                item.getXmlSize(),
                item.getXmlHash(),
                item.getAccessKey(),
                item.getModel(),
                item.getIssueDate(),
                item.getErroCodigo(),
                item.getErroMensagem(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private ImportacaoSummaryResponse toSummaryResponse(Importacao importacao) {
        return new ImportacaoSummaryResponse(
                importacao.getId(),
                importacao.getStatus().name(),
                importacao.getSourceType().name(),
                safeInt(importacao.getTotalEncontrado()),
                safeInt(importacao.getTotalProcessado()),
                safeInt(importacao.getTotalErros()),
                importacao.getCreatedAt(),
                importacao.getUpdatedAt()
        );
    }

    private ImportFailureSummaryResponse toFailureSummary(ImportItemRepository.FailureSummaryProjection projection) {
        return new ImportFailureSummaryResponse(
                StringUtils.hasText(projection.getErroCodigo()) ? projection.getErroCodigo() : "NAO_CLASSIFICADO",
                StringUtils.hasText(projection.getErroMensagem()) ? projection.getErroMensagem() : "sem_detalhe",
                projection.getTotalItems(),
                projection.getSampleItemId(),
                projection.getSampleXmlPath(),
                projection.getLastOccurrenceAt()
        );
    }

    private long safeInt(Integer value) {
        return value == null ? 0 : value.longValue();
    }

    private Importacao loadAndAuthorize(Long importacaoId, UserAuthContext auth) {
        if (importacaoId == null || importacaoId <= 0) {
            throw new ValidationException("id invalido");
        }
        Importacao imp = importacaoRepository.findById(importacaoId)
                .orElseThrow(() -> new ValidationException("Importacao nao encontrada: " + importacaoId));
        userAuthorizationService.assertCanRead(auth, imp.getTenantId(), imp.getEmpresaId());
        return imp;
    }
}
