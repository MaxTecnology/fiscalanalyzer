package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestRequest;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestResponse;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/imports", "/api/importacoes"})
@Validated
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoResponse upload(
            @RequestParam @NotNull Long tenantId,
            @RequestParam @NotNull Long empresaId,
            @RequestPart("file") MultipartFile file
    ) {
        var imp = importacaoService.criarImportacao(tenantId, empresaId, file);
        return new ImportacaoResponse(imp.getId(), imp.getStatus().name());
    }

    @PostMapping(value = "/manifest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ManifestResponse manifest(@RequestBody @Valid ManifestRequest request) {
        var imp = importacaoService.criarImportacaoPorManifesto(request);
        return new ManifestResponse(imp.getId(), imp.getStatus().name(), imp.getTotalEncontrado());
    }

}
