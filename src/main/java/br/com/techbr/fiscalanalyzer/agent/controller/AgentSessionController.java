package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentSessionResponse;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentSessionController {

    private final Integer scanIntervalSeconds;
    private final Integer maxUploadConcurrency;

    public AgentSessionController(
            @Value("${app.agent.scan-interval-seconds:60}") Integer scanIntervalSeconds,
            @Value("${app.agent.max-upload-concurrency:4}") Integer maxUploadConcurrency
    ) {
        this.scanIntervalSeconds = scanIntervalSeconds;
        this.maxUploadConcurrency = maxUploadConcurrency;
    }

    @PostMapping(value = "/session", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AgentSessionResponse session(HttpServletRequest servletRequest) {
        var ctx = AgentAuthRequestContext.required(servletRequest);
        return new AgentSessionResponse(
                ctx.tenantId(),
                ctx.empresaId(),
                scanIntervalSeconds,
                maxUploadConcurrency
        );
    }
}
