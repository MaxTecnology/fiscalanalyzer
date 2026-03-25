package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.identity.dto.BootstrapAdminResponse;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityBootstrapService;
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

class AuthBootstrapControllerTest {

    private MockMvc mockMvc;
    private IdentityBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        bootstrapService = mock(IdentityBootstrapService.class);
        var controller = new AuthBootstrapController(bootstrapService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void bootstrap_retorna201() throws Exception {
        when(bootstrapService.bootstrap(eq("bootstrap-secret"), any()))
                .thenReturn(new BootstrapAdminResponse(10L, 20L, 30L, "admin@empresa.com", "ok"));

        mockMvc.perform(post("/auth/bootstrap-admin")
                        .header("X-Bootstrap-Token", "bootstrap-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "tenantNome": "Tenant A",
                                  "empresaRazaoSocial": "Empresa A LTDA",
                                  "empresaCnpj": "12345678000199",
                                  "nome": "Admin",
                                  "email": "admin@empresa.com",
                                  "password": "SenhaForte123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(10))
                .andExpect(jsonPath("$.empresaId").value(20))
                .andExpect(jsonPath("$.userId").value(30));
    }

    @Test
    void bootstrap_tokenInvalido_retorna403() throws Exception {
        when(bootstrapService.bootstrap(eq("bad-token"), any()))
                .thenThrow(new ForbiddenException("X-Bootstrap-Token invalido"));

        mockMvc.perform(post("/auth/bootstrap-admin")
                        .header("X-Bootstrap-Token", "bad-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "tenantNome": "Tenant A",
                                  "empresaRazaoSocial": "Empresa A LTDA",
                                  "empresaCnpj": "12345678000199",
                                  "nome": "Admin",
                                  "email": "admin@empresa.com",
                                  "password": "SenhaForte123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }
}

