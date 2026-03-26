package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentInstanceStatusResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentInstanceStatusSummaryResponse;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;
import br.com.techbr.fiscalanalyzer.agent.service.AgentPresenceService;
import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAgentMonitorControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentPresenceService service = mock(AgentPresenceService.class);
        AgentInstanceStatusResponse response = new AgentInstanceStatusResponse(
                10L,
                99L,
                42L,
                77L,
                "fa_live_ab",
                "desktop-agent-01",
                AgentInstanceStatusType.ONLINE,
                "1.2.0",
                "host-a",
                1,
                4,
                0,
                null,
                null,
                Instant.parse("2026-03-26T17:50:00Z"),
                Instant.parse("2026-03-26T18:00:00Z"),
                Instant.parse("2026-03-26T17:59:50Z"),
                Instant.parse("2026-03-26T17:59:58Z"),
                Instant.parse("2026-03-26T18:00:00Z")
        );
        when(service.list(99L, 42L, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
        when(service.summary(99L, 42L))
                .thenReturn(new AgentInstanceStatusSummaryResponse(
                        99L, 42L, 1, 1, 0, 0, 0, 0, Instant.parse("2026-03-26T18:00:00Z")
                ));
        when(service.detail(99L, 42L, "desktop-agent-01")).thenReturn(response);

        AdminAgentMonitorController controller = new AdminAgentMonitorController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void list_retorna200() throws Exception {
        mockMvc.perform(get("/admin/empresas/42/agents?tenantId=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].agentId").value("desktop-agent-01"))
                .andExpect(jsonPath("$.content[0].status").value("ONLINE"));
    }

    @Test
    void summary_retorna200() throws Exception {
        mockMvc.perform(get("/admin/empresas/42/agents/summary?tenantId=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.online").value(1))
                .andExpect(jsonPath("$.offline").value(0));
    }

    @Test
    void detail_retorna200() throws Exception {
        mockMvc.perform(get("/admin/empresas/42/agents/desktop-agent-01?tenantId=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("desktop-agent-01"))
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }
}
