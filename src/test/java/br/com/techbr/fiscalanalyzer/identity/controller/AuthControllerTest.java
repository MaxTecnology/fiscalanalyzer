package br.com.techbr.fiscalanalyzer.identity.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthLoginResponse;
import br.com.techbr.fiscalanalyzer.identity.dto.AuthUserInfoResponse;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.IdentityAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private IdentityAuthService service;

    @BeforeEach
    void setUp() {
        service = mock(IdentityAuthService.class);
        var controller = new AuthController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void login_retorna200() throws Exception {
        var user = new AuthUserInfoResponse(
                10L,
                "Admin",
                "admin@empresa.com",
                "ATIVO",
                Instant.now(),
                List.of("ADMIN"),
                List.of()
        );
        when(service.login(any())).thenReturn(new AuthLoginResponse(
                "jwt-token",
                "Bearer",
                Instant.now().plusSeconds(3600),
                user
        ));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@empresa.com\",\"password\":\"SenhaForte123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("admin@empresa.com"));
    }

    @Test
    void me_retorna200() throws Exception {
        when(service.me(any())).thenReturn(new AuthUserInfoResponse(
                7L,
                "Operador",
                "operador@empresa.com",
                "ATIVO",
                Instant.now(),
                List.of("OPERADOR"),
                List.of()
        ));

        mockMvc.perform(get("/auth/me")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(7))
                .andExpect(jsonPath("$.user.roles[0]").value("OPERADOR"));
    }
}

