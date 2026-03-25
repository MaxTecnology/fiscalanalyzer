package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AdminAgentKeyResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AdminCreateAgentKeyRequest;
import br.com.techbr.fiscalanalyzer.agent.service.AgentApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/admin/empresas")
public class AdminAgentKeyController {

    private final AgentApiKeyService agentApiKeyService;

    public AdminAgentKeyController(AgentApiKeyService agentApiKeyService) {
        this.agentApiKeyService = agentApiKeyService;
    }

    @PostMapping("/{empresaId}/agent-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAgentKeyResponse create(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestBody(required = false) @Valid AdminCreateAgentKeyRequest request
    ) {
        return agentApiKeyService.createKey(tenantId, empresaId, request);
    }

    @GetMapping("/{empresaId}/agent-keys")
    public List<AdminAgentKeyResponse> list(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @Min(1) Long tenantId
    ) {
        return agentApiKeyService.listKeys(tenantId, empresaId);
    }

    @DeleteMapping("/{empresaId}/agent-keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @PathVariable @NotNull @Min(1) Long keyId,
            @RequestParam @NotNull @Min(1) Long tenantId
    ) {
        agentApiKeyService.revokeKey(tenantId, empresaId, keyId);
    }

    @PostMapping("/{empresaId}/agent-keys/{keyId}/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAgentKeyResponse rotate(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @PathVariable @NotNull @Min(1) Long keyId,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam(defaultValue = "false") boolean revokeOld,
            @RequestBody(required = false) @Valid AdminCreateAgentKeyRequest request
    ) {
        return agentApiKeyService.rotateKey(tenantId, empresaId, keyId, revokeOld, request);
    }
}
