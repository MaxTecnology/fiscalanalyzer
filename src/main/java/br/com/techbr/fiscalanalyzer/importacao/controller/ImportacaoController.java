package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoResponse;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoService;
import jakarta.validation.constraints.NotNull;
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
        Importacao imp = importacaoService.criarImportacao(tenantId, empresaId, file);
        return new ImportacaoResponse(imp.getId(), imp.getStatus().name());
    }

    @GetMapping("/{id}")
    public ImportacaoResponse status(@PathVariable Long id) {
        Importacao imp = importacaoService.buscarPorId(id); // vamos criar
        return new ImportacaoResponse(imp.getId(), imp.getStatus().name());
    }

}
