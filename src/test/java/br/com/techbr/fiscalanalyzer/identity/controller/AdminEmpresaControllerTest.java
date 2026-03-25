package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.identity.dto.EmpresaResponse;
import br.com.techbr.fiscalanalyzer.identity.model.EmpresaStatus;
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

class AdminEmpresaControllerTest {

    private MockMvc mockMvc;
    private IdentityAdminService service;

    @BeforeEach
    void setUp() {
        service = mock(IdentityAdminService.class);
        var controller = new AdminEmpresaController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void create_retorna201() throws Exception {
        when(service.createEmpresa(eq(1L), any())).thenReturn(new EmpresaResponse(
                2L, 1L, "12345678000199", "Empresa A", "ATIVA", Instant.now(), Instant.now()
        ));

        mockMvc.perform(post("/admin/tenants/1/empresas")
                        .contentType("application/json")
                        .content("{\"cnpj\":\"12.345.678/0001-99\",\"razaoSocial\":\"Empresa A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.tenantId").value(1));
    }

    @Test
    void list_retorna200() throws Exception {
        when(service.listEmpresas(1L, EmpresaStatus.ATIVA)).thenReturn(List.of(
                new EmpresaResponse(2L, 1L, "12345678000199", "Empresa A", "ATIVA", Instant.now(), Instant.now())
        ));

        mockMvc.perform(get("/admin/tenants/1/empresas?status=ATIVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    void updateStatus_retorna200() throws Exception {
        when(service.updateEmpresaStatus(eq(1L), eq(2L), any())).thenReturn(new EmpresaResponse(
                2L, 1L, "12345678000199", "Empresa A", "INATIVA", Instant.now(), Instant.now()
        ));

        mockMvc.perform(patch("/admin/tenants/1/empresas/2/status")
                        .contentType("application/json")
                        .content("{\"status\":\"INATIVA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVA"));
    }
}
