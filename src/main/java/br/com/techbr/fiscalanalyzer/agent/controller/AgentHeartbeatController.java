package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentHeartbeatRequest;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentHeartbeatResponse;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import br.com.techbr.fiscalanalyzer.agent.security.AgentRequestMetadata;
import br.com.techbr.fiscalanalyzer.agent.service.AgentPresenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentHeartbeatController {

    private final AgentPresenceService agentPresenceService;

    public AgentHeartbeatController(AgentPresenceService agentPresenceService) {
        this.agentPresenceService = agentPresenceService;
    }

    @PostMapping(value = "/heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AgentHeartbeatResponse heartbeat(
            @RequestHeader(name = "X-Agent-Id", required = false) String agentId,
            @RequestBody(required = false) AgentHeartbeatRequest request,
            HttpServletRequest servletRequest
    ) {
        var ctx = AgentAuthRequestContext.required(servletRequest);
        return agentPresenceService.heartbeat(ctx, agentId, AgentRequestMetadata.ipHash(servletRequest), request);
    }
}
