package br.com.techbr.fiscalanalyzer.agent.controller;

import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentSessionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentSessionController controller = new AgentSessionController(60, 4);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void session_retorna200_quandoContextoPresente() throws Exception {
        mockMvc.perform(post("/agent/session")
                        .contentType("application/json")
                        .content("{}")
                        .requestAttr(AgentAuthRequestContext.REQUEST_ATTRIBUTE,
                                new AgentAuthContext(1L, 99L, 42L, "fa_live_ab")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(99))
                .andExpect(jsonPath("$.empresaId").value(42))
                .andExpect(jsonPath("$.scanIntervalSeconds").value(60))
                .andExpect(jsonPath("$.maxUploadConcurrency").value(4));
    }

    @Test
    void session_retorna401_quandoContextoAusente() throws Exception {
        mockMvc.perform(post("/agent/session")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }
}
