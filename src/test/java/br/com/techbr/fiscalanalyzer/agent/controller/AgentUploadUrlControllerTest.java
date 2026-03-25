package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlRequest;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlResponse;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import br.com.techbr.fiscalanalyzer.agent.service.AgentUploadUrlService;
import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.common.exception.ConflictException;
import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class AgentUploadUrlControllerTest {

    private MockMvc mockMvc;
    private AgentUploadUrlService service;

    @BeforeEach
    void setUp() {
        service = mock(AgentUploadUrlService.class);
        AgentUploadUrlController controller = new AgentUploadUrlController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void uploadUrl_retorna200() throws Exception {
        when(service.generateUploadUrl(any(), any(AgentUploadUrlRequest.class)))
                .thenReturn(new AgentUploadUrlResponse(
                        "https://upload.example", "99/42/hash.xml", 900
                ));

        String body = """
                {
                  "sha256": "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                  "originalFileName": "nfe-001.xml",
                  "sizeBytes": 1500
                }
                """;

        mockMvc.perform(post("/agent/upload-url")
                        .requestAttr(AgentAuthRequestContext.REQUEST_ATTRIBUTE, new AgentAuthContext(1L, 99L, 42L, "fa_live_ab"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("99/42/hash.xml"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void uploadUrl_semContexto_retorna401() throws Exception {
        String body = """
                {
                  "sha256": "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                  "originalFileName": "nfe-001.xml",
                  "sizeBytes": 1500
                }
                """;

        mockMvc.perform(post("/agent/upload-url")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void uploadUrl_duplicado_retorna409() throws Exception {
        when(service.generateUploadUrl(any(), any(AgentUploadUrlRequest.class)))
                .thenThrow(new ConflictException("Objeto ja existe para sha256 informado"));

        String body = """
                {
                  "sha256": "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                  "originalFileName": "nfe-001.xml",
                  "sizeBytes": 1500
                }
                """;

        mockMvc.perform(post("/agent/upload-url")
                        .requestAttr(AgentAuthRequestContext.REQUEST_ATTRIBUTE, new AgentAuthContext(1L, 99L, 42L, "fa_live_ab"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void uploadUrl_rateLimited_retorna429ComRetryAfter() throws Exception {
        when(service.generateUploadUrl(any(), any(AgentUploadUrlRequest.class)))
                .thenThrow(new TooManyRequestsException("Limite excedido", 7));

        String body = """
                {
                  "sha256": "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
                  "originalFileName": "nfe-001.xml",
                  "sizeBytes": 1500
                }
                """;

        mockMvc.perform(post("/agent/upload-url")
                        .requestAttr(AgentAuthRequestContext.REQUEST_ATTRIBUTE, new AgentAuthContext(1L, 99L, 42L, "fa_live_ab"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
