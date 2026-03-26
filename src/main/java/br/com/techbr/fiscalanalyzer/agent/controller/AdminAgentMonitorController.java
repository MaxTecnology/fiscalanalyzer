package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentInstanceStatusResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentInstanceStatusSummaryResponse;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;
import br.com.techbr.fiscalanalyzer.agent.service.AgentPresenceService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/admin/empresas")
public class AdminAgentMonitorController {

    private final AgentPresenceService agentPresenceService;

    public AdminAgentMonitorController(AgentPresenceService agentPresenceService) {
        this.agentPresenceService = agentPresenceService;
    }

    @GetMapping("/{empresaId}/agents")
    public Page<AgentInstanceStatusResponse> list(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @Min(1) Long tenantId,
            @RequestParam(required = false) AgentInstanceStatusType status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return agentPresenceService.list(
                tenantId,
                empresaId,
                status,
                PageRequest.of(safePage, safeSize)
        );
    }

    @GetMapping("/{empresaId}/agents/summary")
    public AgentInstanceStatusSummaryResponse summary(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @RequestParam @NotNull @Min(1) Long tenantId
    ) {
        return agentPresenceService.summary(tenantId, empresaId);
    }

    @GetMapping("/{empresaId}/agents/{agentId}")
    public AgentInstanceStatusResponse detail(
            @PathVariable @NotNull @Min(1) Long empresaId,
            @PathVariable @NotBlank String agentId,
            @RequestParam @NotNull @Min(1) Long tenantId
    ) {
        return agentPresenceService.detail(tenantId, empresaId, agentId);
    }
}
