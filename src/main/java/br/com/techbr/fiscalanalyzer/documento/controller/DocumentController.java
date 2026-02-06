package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentResponse;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
@Validated
public class DocumentController {

    private final DocumentQueryService documentQueryService;

    public DocumentController(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    @GetMapping("/{accessKey}")
    public FiscalDocumentResponse getByAccessKey(
            @PathVariable String accessKey,
            @RequestParam @NotNull Long tenantId,
            @RequestParam @NotNull Long empresaId
    ) {
        return documentQueryService.findByAccessKey(tenantId, empresaId, accessKey);
    }
}
