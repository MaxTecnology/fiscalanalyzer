package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AdminAgentKeyResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AdminCreateAgentKeyRequest;
import br.com.techbr.fiscalanalyzer.agent.service.AgentApiKeyService;
import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAgentKeyControllerTest {

    private MockMvc mockMvc;
    private AgentApiKeyService service;

    @BeforeEach
    void setUp() {
        service = mock(AgentApiKeyService.class);
        AdminAgentKeyController controller = new AdminAgentKeyController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void create_retorna201() throws Exception {
        when(service.createKey(eq(1L), eq(2L), any(AdminCreateAgentKeyRequest.class)))
                .thenReturn(new AdminAgentKeyResponse(
                        10L, 1L, 2L, "fa_live_ab", "srv", true, null, Instant.now(), null, "fa_live_secret"
                ));

        mockMvc.perform(post("/admin/empresas/2/agent-keys?tenantId=1")
                        .contentType("application/json")
                        .content("{\"descricao\":\"srv\",\"criadoPor\":\"admin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.apiKey").value("fa_live_secret"));
    }

    @Test
    void list_retorna200() throws Exception {
        when(service.listKeys(1L, 2L)).thenReturn(List.of(
                new AdminAgentKeyResponse(10L, 1L, 2L, "fa_live_ab", "srv", true, null, Instant.now(), null, null)
        ));

        mockMvc.perform(get("/admin/empresas/2/agent-keys?tenantId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].apiKey").value(nullValue()));
    }

    @Test
    void revoke_retorna204() throws Exception {
        doNothing().when(service).revokeKey(1L, 2L, 10L);

        mockMvc.perform(delete("/admin/empresas/2/agent-keys/10?tenantId=1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rotate_retorna201() throws Exception {
        when(service.rotateKey(eq(1L), eq(2L), eq(10L), eq(true), any(AdminCreateAgentKeyRequest.class)))
                .thenReturn(new AdminAgentKeyResponse(
                        11L, 1L, 2L, "fa_live_cd", "rotated", true, null, Instant.now(), null, "fa_live_new"
                ));

        mockMvc.perform(post("/admin/empresas/2/agent-keys/10/rotate?tenantId=1&revokeOld=true")
                        .contentType("application/json")
                        .content("{\"descricao\":\"rotated\",\"criadoPor\":\"admin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.apiKey").value("fa_live_new"));
    }
}
