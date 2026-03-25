package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlRequest;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlResponse;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import br.com.techbr.fiscalanalyzer.agent.service.AgentUploadUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentUploadUrlController {

    private final AgentUploadUrlService agentUploadUrlService;

    public AgentUploadUrlController(AgentUploadUrlService agentUploadUrlService) {
        this.agentUploadUrlService = agentUploadUrlService;
    }

    @PostMapping(value = "/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AgentUploadUrlResponse uploadUrl(@RequestBody @Valid AgentUploadUrlRequest request,
                                            HttpServletRequest servletRequest) {
        var auth = AgentAuthRequestContext.required(servletRequest);
        return agentUploadUrlService.generateUploadUrl(auth, request);
    }
}
