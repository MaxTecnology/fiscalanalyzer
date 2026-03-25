package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoDetailResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportItemResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoSummaryResponse;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoReadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/imports")
@Validated
public class ImportacaoReadController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "status", "createdAt", "updatedAt");

    private final ImportacaoReadService importacaoReadService;

    public ImportacaoReadController(ImportacaoReadService importacaoReadService) {
        this.importacaoReadService = importacaoReadService;
    }

    @GetMapping
    public Page<ImportacaoSummaryResponse> list(
            @RequestParam @Min(1) Long tenantId,
            @RequestParam @Min(1) Long empresaId,
            @RequestParam(required = false) ImportacaoStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            HttpServletRequest servletRequest
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        return importacaoReadService.listImports(
                tenantId,
                empresaId,
                status,
                pageable,
                UserAuthRequestContext.required(servletRequest)
        );
    }

    @GetMapping("/{id}")
    public ImportacaoDetailResponse get(@PathVariable @Min(1) Long id,
                                        HttpServletRequest servletRequest) {
        return importacaoReadService.getDetail(id, UserAuthRequestContext.required(servletRequest));
    }

    @GetMapping("/{id}/items")
    public Page<ImportItemResponse> listItems(
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) ImportItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return importacaoReadService.listItems(id, status, pageable, UserAuthRequestContext.required(servletRequest));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String[] sortParts = sort == null ? new String[0] : sort.split(",");
        String property = sortParts.length > 0 ? sortParts[0].trim() : "createdAt";
        if (!ALLOWED_SORT_PROPERTIES.contains(property)) {
            throw new ValidationException("sort invalido: " + property);
        }
        Sort.Direction direction = (sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(direction, property));
    }
}
