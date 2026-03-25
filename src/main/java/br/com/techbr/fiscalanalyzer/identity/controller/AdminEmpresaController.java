package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.identity.dto.CreateEmpresaRequest;
import br.com.techbr.fiscalanalyzer.identity.dto.EmpresaResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.UpdateEmpresaStatusRequest;
import br.com.techbr.fiscalanalyzer.identity.model.EmpresaStatus;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/tenants/{tenantId}/empresas")
@Validated
public class AdminEmpresaController {

    private final IdentityAdminService identityAdminService;

    public AdminEmpresaController(IdentityAdminService identityAdminService) {
        this.identityAdminService = identityAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaResponse create(@PathVariable @Min(1) Long tenantId,
                                  @RequestBody @Valid CreateEmpresaRequest request) {
        return identityAdminService.createEmpresa(tenantId, request);
    }

    @GetMapping
    public List<EmpresaResponse> list(@PathVariable @Min(1) Long tenantId,
                                      @RequestParam(required = false) EmpresaStatus status) {
        return identityAdminService.listEmpresas(tenantId, status);
    }

    @GetMapping("/{empresaId}")
    public EmpresaResponse get(@PathVariable @Min(1) Long tenantId,
                               @PathVariable @Min(1) Long empresaId) {
        return identityAdminService.getEmpresa(tenantId, empresaId);
    }

    @PatchMapping("/{empresaId}/status")
    public EmpresaResponse updateStatus(@PathVariable @Min(1) Long tenantId,
                                        @PathVariable @Min(1) Long empresaId,
                                        @RequestBody @Valid UpdateEmpresaStatusRequest request) {
        return identityAdminService.updateEmpresaStatus(tenantId, empresaId, request);
    }
}
