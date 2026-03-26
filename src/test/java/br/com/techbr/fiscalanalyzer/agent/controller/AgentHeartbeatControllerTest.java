package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentHeartbeatResponse;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import br.com.techbr.fiscalanalyzer.agent.service.AgentPresenceService;
import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentHeartbeatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentPresenceService service = mock(AgentPresenceService.class);
        when(service.heartbeat(any(), any(), any(), any()))
                .thenReturn(new AgentHeartbeatResponse(
                        99L,
                        42L,
                        "desktop-agent-01",
                        AgentInstanceStatusType.ONLINE,
                        30,
                        Instant.parse("2026-03-26T18:00:00Z")
                ));

        AgentHeartbeatController controller = new AgentHeartbeatController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void heartbeat_retorna200_quandoContextoPresente() throws Exception {
        mockMvc.perform(post("/agent/heartbeat")
                        .contentType("application/json")
                        .content("{\"uploadsInFlight\":2}")
                        .requestAttr(
                                AgentAuthRequestContext.REQUEST_ATTRIBUTE,
                                new AgentAuthContext(1L, 99L, 42L, "fa_live_ab")
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(99))
                .andExpect(jsonPath("$.empresaId").value(42))
                .andExpect(jsonPath("$.agentId").value("desktop-agent-01"))
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test
    void heartbeat_retorna401_quandoContextoAusente() throws Exception {
        mockMvc.perform(post("/agent/heartbeat")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }
}
