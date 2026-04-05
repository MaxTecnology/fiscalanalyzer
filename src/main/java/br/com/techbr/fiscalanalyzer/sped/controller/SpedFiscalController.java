package br.com.techbr.fiscalanalyzer.sped.controller;

import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedFiscalFileDetailResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedFiscalFileSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationDivergenceResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationItemResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedUploadResponse;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.model.SpedReconciliationStatus;
import br.com.techbr.fiscalanalyzer.sped.service.SpedFiscalIngestionService;
import br.com.techbr.fiscalanalyzer.sped.service.SpedFiscalReadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/sped")
@Validated
public class SpedFiscalController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "status", "createdAt", "updatedAt");

    private final SpedFiscalIngestionService spedFiscalIngestionService;
    private final SpedFiscalReadService spedFiscalReadService;
    private final UserAuthorizationService userAuthorizationService;

    public SpedFiscalController(SpedFiscalIngestionService spedFiscalIngestionService,
                                SpedFiscalReadService spedFiscalReadService,
                                UserAuthorizationService userAuthorizationService) {
        this.spedFiscalIngestionService = spedFiscalIngestionService;
        this.spedFiscalReadService = spedFiscalReadService;
        this.userAuthorizationService = userAuthorizationService;
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpedUploadResponse uploadSped(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest servletRequest
    ) {
        var auth = UserAuthRequestContext.required(servletRequest);
        userAuthorizationService.assertCanWrite(auth, tenantId, empresaId);
        return spedFiscalIngestionService.upload(tenantId, empresaId, file);
    }

    @GetMapping("/files")
    public Page<SpedFiscalFileSummaryResponse> list(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(required = false) SpedFiscalFileStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            HttpServletRequest servletRequest
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        return spedFiscalReadService.list(tenantId, empresaId, status, pageable, UserAuthRequestContext.required(servletRequest));
    }

    @GetMapping("/files/{id}")
    public SpedFiscalFileDetailResponse detail(
            @PathVariable @NotNull @Min(1) Long id,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            HttpServletRequest servletRequest
    ) {
        return spedFiscalReadService.detail(id, tenantId, empresaId, UserAuthRequestContext.required(servletRequest));
    }

    @PostMapping("/files/{id}/reprocess")
    public SpedFiscalFileDetailResponse reprocess(
            @PathVariable @NotNull @Min(1) Long id,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            HttpServletRequest servletRequest
    ) {
        return spedFiscalReadService.reprocess(id, tenantId, empresaId, UserAuthRequestContext.required(servletRequest));
    }

    @GetMapping("/files/{id}/reconciliation")
    public Page<SpedReconciliationItemResponse> reconciliation(
            @PathVariable @NotNull @Min(1) Long id,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest servletRequest
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500), Sort.by(Sort.Direction.ASC, "linhaOrigem"));
        return spedFiscalReadService.reconciliation(id, tenantId, empresaId, pageable, UserAuthRequestContext.required(servletRequest));
    }

    @GetMapping("/reconciliation/summary")
    public SpedReconciliationSummaryResponse reconciliationSummary(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoInicio,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoFim,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest servletRequest
    ) {
        return spedFiscalReadService.reconciliationSummary(
                tenantId,
                empresaId,
                periodoInicio,
                periodoFim,
                limit,
                UserAuthRequestContext.required(servletRequest)
        );
    }

    @GetMapping("/reconciliation/divergences")
    public Page<SpedReconciliationDivergenceResponse> reconciliationDivergences(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoInicio,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoFim,
            @RequestParam(required = false) SpedReconciliationStatus status,
            @RequestParam(required = false) String accessKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest servletRequest
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
        return spedFiscalReadService.reconciliationDivergences(
                tenantId,
                empresaId,
                periodoInicio,
                periodoFim,
                status,
                accessKey,
                pageable,
                UserAuthRequestContext.required(servletRequest)
        );
    }

    @GetMapping("/summary")
    public SpedSummaryResponse summary(
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam @NotNull @Min(1) Long empresaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoFim,
            HttpServletRequest servletRequest
    ) {
        return spedFiscalReadService.summary(
                tenantId,
                empresaId,
                periodoInicio,
                periodoFim,
                UserAuthRequestContext.required(servletRequest)
        );
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
