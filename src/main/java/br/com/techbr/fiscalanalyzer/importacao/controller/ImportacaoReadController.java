package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoDetailResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportItemResponse;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoReadService;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/imports")
@Validated
public class ImportacaoReadController {

    private final ImportacaoReadService importacaoReadService;

    public ImportacaoReadController(ImportacaoReadService importacaoReadService) {
        this.importacaoReadService = importacaoReadService;
    }

    @GetMapping("/{id}")
    public ImportacaoDetailResponse get(@PathVariable @Min(1) Long id) {
        return importacaoReadService.getDetail(id);
    }

    @GetMapping("/{id}/items")
    public Page<ImportItemResponse> listItems(
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) ImportItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return importacaoReadService.listItems(id, status, pageable);
    }
}
