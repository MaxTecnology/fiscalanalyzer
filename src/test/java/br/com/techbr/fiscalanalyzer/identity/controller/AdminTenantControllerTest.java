package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.identity.dto.TenantResponse;
import br.com.techbr.fiscalanalyzer.identity.model.TenantStatus;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTenantControllerTest {

    private MockMvc mockMvc;
    private IdentityAdminService service;

    @BeforeEach
    void setUp() {
        service = mock(IdentityAdminService.class);
        var controller = new AdminTenantController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void create_retorna201() throws Exception {
        when(service.createTenant(any())).thenReturn(new TenantResponse(
                10L, "Cliente A", "ATIVO", Instant.now(), Instant.now()
        ));

        mockMvc.perform(post("/admin/tenants")
                        .contentType("application/json")
                        .content("{\"nome\":\"Cliente A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void list_retorna200() throws Exception {
        when(service.listTenants(TenantStatus.ATIVO)).thenReturn(List.of(
                new TenantResponse(10L, "Cliente A", "ATIVO", Instant.now(), Instant.now())
        ));

        mockMvc.perform(get("/admin/tenants?status=ATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void updateStatus_retorna200() throws Exception {
        when(service.updateTenantStatus(eq(10L), any())).thenReturn(new TenantResponse(
                10L, "Cliente A", "INATIVO", Instant.now(), Instant.now()
        ));

        mockMvc.perform(patch("/admin/tenants/10/status")
                        .contentType("application/json")
                        .content("{\"status\":\"INATIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"));
    }
}
