package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.identity.dto.UserEmpresaAccessResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.UserResponse;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    private MockMvc mockMvc;
    private IdentityAdminService service;

    @BeforeEach
    void setUp() {
        service = mock(IdentityAdminService.class);
        var controller = new AdminUserController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void create_retorna201() throws Exception {
        when(service.createUser(any())).thenReturn(userResponse());

        mockMvc.perform(post("/admin/users")
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome":"Operador A",
                                  "email":"operador@empresa.com",
                                  "password":"SenhaForte123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("operador@empresa.com"));
    }

    @Test
    void list_retorna200() throws Exception {
        when(service.listUsers(AppUserStatus.ATIVO)).thenReturn(List.of(userResponse()));

        mockMvc.perform(get("/admin/users?status=ATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
    }

    @Test
    void addRole_retorna200() throws Exception {
        when(service.addRole(7L, "ADMIN")).thenReturn(userResponse());

        mockMvc.perform(put("/admin/users/7/roles/ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void addEmpresaAccess_retorna200() throws Exception {
        when(service.addEmpresaAccess(eq(7L), eq(1L), eq(2L))).thenReturn(userResponse());

        mockMvc.perform(put("/admin/users/7/tenants/1/empresas/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresas[0].tenantId").value(1))
                .andExpect(jsonPath("$.empresas[0].empresaId").value(2));
    }

    private UserResponse userResponse() {
        return new UserResponse(
                7L,
                "Operador A",
                "operador@empresa.com",
                "ATIVO",
                Instant.now(),
                Instant.now(),
                null,
                List.of("ADMIN"),
                List.of(new UserEmpresaAccessResponse(1L, 2L))
        );
    }
}
