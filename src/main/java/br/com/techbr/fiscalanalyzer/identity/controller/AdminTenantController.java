package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.identity.dto.CreateTenantRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.TenantResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.UpdateTenantStatusRequest;
import br.com.techbr.fiscalanalyzer.identity.model.TenantStatus;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/tenants")
@Validated
public class AdminTenantController {

    private final IdentityAdminService identityAdminService;

    public AdminTenantController(IdentityAdminService identityAdminService) {
        this.identityAdminService = identityAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@RequestBody @Valid CreateTenantRequest request) {
        return identityAdminService.createTenant(request);
    }

    @GetMapping
    public List<TenantResponse> list(@RequestParam(required = false) TenantStatus status) {
        return identityAdminService.listTenants(status);
    }

    @GetMapping("/{tenantId}")
    public TenantResponse get(@PathVariable @Min(1) Long tenantId) {
        return identityAdminService.getTenant(tenantId);
    }

    @PatchMapping("/{tenantId}/status")
    public TenantResponse updateStatus(@PathVariable @Min(1) Long tenantId,
                                       @RequestBody @Valid UpdateTenantStatusRequest request) {
        return identityAdminService.updateTenantStatus(tenantId, request);
    }
}
