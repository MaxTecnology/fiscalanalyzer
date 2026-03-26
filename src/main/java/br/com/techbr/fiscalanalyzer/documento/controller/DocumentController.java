package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageBackfillResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentResponse;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentCoverageService;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
@Validated
public class DocumentController {

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

    @GetMapping("/{accessKey}")
    public FiscalDocumentResponse getByAccessKey(
            @PathVariable String accessKey,
            @RequestParam @NotNull Long tenantId,
            @RequestParam @NotNull Long empresaId,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanRead(auth, tenantId, empresaId);
        return documentQueryService.findByAccessKey(tenantId, empresaId, accessKey);
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
}
