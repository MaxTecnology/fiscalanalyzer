package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCodeStatsResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageBackfillResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentExportRequest;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentListItemResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentPartyResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentDetailResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentEventResponse;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentCoverageService;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/documents")
@Validated
public class DocumentController {

    private static final Map<String, String> ALLOWED_SORT_PROPERTIES = Map.of(
            "issueDate", "issueDate",
            "totalAmount", "totalAmount",
            "createdAt", "createdAt",
            "nfeNumero", "numeroNota",
            "accessKey", "accessKey"
    );

    private final DocumentQueryService documentQueryService;
    private final DocumentCoverageService documentCoverageService;
    private final UserAuthorizationService userAuthorizationService;

    public DocumentController(DocumentQueryService documentQueryService,
                              DocumentCoverageService documentCoverageService,
                              UserAuthorizationService userAuthorizationService) {
        this.documentQueryService = documentQueryService;
        this.documentCoverageService = documentCoverageService;
        this.userAuthorizationService = userAuthorizationService;
    }

    @GetMapping
    public Page<DocumentListItemResponse> list(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(required = false) Short model,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) FiscalDocumentStatus statusDocumento,
            @RequestParam(required = false) String emitCnpj,
            @RequestParam(required = false) String destCnpj,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateTo,
            @RequestParam(required = false) Long importacaoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "issueDate,desc") String sort,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);

        Pageable pageable = buildPageable(page, size, sort);
        return documentQueryService.listDocuments(
                tenantId,
                empresaId,
                model,
                operationType,
                statusDocumento,
                emitCnpj,
                destCnpj,
                issueDateFrom,
                issueDateTo,
                importacaoId,
                pageable
        );
    }

    @GetMapping("/{accessKey}")
    public FiscalDocumentDetailResponse getByAccessKey(
            @PathVariable String accessKey,
            @RequestParam @NotNull Long tenantId,
            @RequestParam @NotNull Long empresaId,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.findByAccessKey(tenantId, empresaId, accessKey);
    }

    @GetMapping("/{accessKey}/xml")
    public ResponseEntity<byte[]> downloadXml(
            @PathVariable String accessKey,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);

        var xml = documentQueryService.downloadXml(tenantId, empresaId, accessKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDisposition(ContentDisposition.attachment().filename(xml.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(xml.payload());
    }

    @GetMapping("/{accessKey}/danfe")
    public ResponseEntity<byte[]> downloadDanfe(
            @PathVariable String accessKey,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);

        var danfe = documentQueryService.downloadDanfe(tenantId, empresaId, accessKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(danfe.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(danfe.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(danfe.payload());
    }

    @GetMapping("/{accessKey}/events")
    public List<FiscalDocumentEventResponse> listEvents(
            @PathVariable String accessKey,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.listEvents(tenantId, empresaId, accessKey);
    }

    @GetMapping("/summary")
    public DocumentSummaryResponse summary(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateFrom,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateTo,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.summarize(tenantId, empresaId, issueDateFrom, issueDateTo);
    }

    @GetMapping("/emitters")
    public List<DocumentPartyResponse> emitters(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") Integer limit,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.listEmitters(tenantId, empresaId, q, limit);
    }

    @GetMapping("/receivers")
    public List<DocumentPartyResponse> receivers(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") Integer limit,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.listReceivers(tenantId, empresaId, q, limit);
    }

    @GetMapping("/stats/cfop")
    public List<DocumentCodeStatsResponse> statsCfop(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateFrom,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateTo,
            @RequestParam(defaultValue = "50") Integer limit,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.statsByCfop(tenantId, empresaId, issueDateFrom, issueDateTo, limit);
    }

    @GetMapping("/stats/ncm")
    public List<DocumentCodeStatsResponse> statsNcm(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateFrom,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateTo,
            @RequestParam(defaultValue = "50") Integer limit,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.statsByNcm(tenantId, empresaId, issueDateFrom, issueDateTo, limit);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportDocuments(
            @Valid @RequestBody DocumentExportRequest request,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, request.tenantId(), request.empresaId());

        var file = documentQueryService.exportDocuments(request);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(file.payload());
    }

    @GetMapping("/coverage")
    public DocumentCoverageSummaryResponse coverageSummary(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentCoverageService.summarize(tenantId, empresaId);
    }

    @PostMapping("/coverage/backfill")
    public DocumentCoverageBackfillResponse backfillCoverage(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(defaultValue = "500") Integer limit,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanWrite(auth, tenantId, empresaId);
        return documentCoverageService.backfill(tenantId, empresaId, limit);
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String[] sortParts = sort == null ? new String[0] : sort.split(",");
        String property = sortParts.length > 0 ? sortParts[0].trim() : "issueDate";
        String mappedProperty = ALLOWED_SORT_PROPERTIES.get(property);
        if (mappedProperty == null) {
            throw new ValidationException("sort invalido: " + property);
        }

        Sort.Direction direction = (sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, mappedProperty));
    }
}
