package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentResponse;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
@Validated
public class DocumentController {

    private final DocumentQueryService documentQueryService;
    private final UserAuthorizationService userAuthorizationService;

    public DocumentController(DocumentQueryService documentQueryService,
                              UserAuthorizationService userAuthorizationService) {
        this.documentQueryService = documentQueryService;
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
}
