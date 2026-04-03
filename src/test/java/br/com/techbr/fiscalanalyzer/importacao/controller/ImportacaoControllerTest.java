package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportReprocessResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestRequest;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoReprocessService;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportacaoControllerTest {

    private MockMvc mockMvc;
    private ImportacaoService importacaoService;
    private ImportacaoReprocessService importacaoReprocessService;
    private UserAuthorizationService userAuthorizationService;

    @BeforeEach
    void setUp() {
        importacaoService = mock(ImportacaoService.class);
        importacaoReprocessService = mock(ImportacaoReprocessService.class);
        userAuthorizationService = mock(UserAuthorizationService.class);
        ImportacaoController controller = new ImportacaoController(
                importacaoService,
                importacaoReprocessService,
                userAuthorizationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void upload_retorna200() throws Exception {
        Importacao imp = new Importacao();
        ReflectionTestUtils.setField(imp, "id", 1L);
        imp.setStatus(ImportacaoStatus.RECEBIDO);

        when(importacaoService.criarImportacao(eq(1L), eq(2L), any())).thenReturn(imp);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teste.zip",
                "application/zip",
                "abc".getBytes()
        );

        mockMvc.perform(multipart("/imports/upload")
                        .file(file)
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", java.util.List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importacaoId").value(1))
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    @Test
    void upload_retorna400_quandoValidacaoFalha() throws Exception {
        when(importacaoService.criarImportacao(eq(1L), eq(2L), any()))
                .thenThrow(new ValidationException("Arquivo ZIP vazio"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teste.zip",
                "application/zip",
                new byte[0]
        );

        mockMvc.perform(multipart("/imports/upload")
                        .file(file)
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", java.util.List.of("OPERADOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void manifest_retorna202() throws Exception {
        Importacao imp = new Importacao();
        ReflectionTestUtils.setField(imp, "id", 42L);
        imp.setStatus(ImportacaoStatus.RECEBIDO);
        imp.setSourceType(ImportacaoSourceType.MANIFEST);
        imp.setTotalEncontrado(1);

        when(importacaoService.criarImportacaoPorManifesto(any(ManifestRequest.class), anyLong(), anyLong())).thenReturn(imp);

        String body = """
                {
                  "tenantId": 1,
                  "empresaId": 2,
                  "entries": [
                    {
                      "objectKey": "1/2/a.xml",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "sizeBytes": 123,
                      "originalFileName": "a.xml"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/imports/manifest")
                        .requestAttr(AgentAuthRequestContext.REQUEST_ATTRIBUTE, new AgentAuthContext(1L, 1L, 2L, "fa_live_abcd"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.importacaoId").value(42))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void manifest_retorna422_quandoObjectKeyNaoExiste() throws Exception {
        when(importacaoService.criarImportacaoPorManifesto(any(ManifestRequest.class), anyLong(), anyLong()))
                .thenThrow(new UnprocessableEntityException("objectKey nao encontrado no storage: 1/2/a.xml"));

        String body = """
                {
                  "tenantId": 1,
                  "empresaId": 2,
                  "entries": [
                    {
                      "objectKey": "1/2/a.xml",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/imports/manifest")
                        .requestAttr(AgentAuthRequestContext.REQUEST_ATTRIBUTE, new AgentAuthContext(1L, 1L, 2L, "fa_live_abcd"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void reprocess_retorna200() throws Exception {
        when(importacaoReprocessService.reprocess(eq(42L), any(), any()))
                .thenReturn(new ImportReprocessResponse(
                        42L,
                        false,
                        "corr-id",
                        10,
                        10,
                        List.of("FALHA_PARSE"),
                        "FALHA_PARSE"
                ));

        mockMvc.perform(post("/imports/42/reprocess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statuses": ["FALHA_PARSE"],
                                  "erroCodigo": "FALHA_PARSE",
                                  "limit": 10
                                }
                                """)
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", java.util.List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importacaoId").value(42))
                .andExpect(jsonPath("$.requeuedItems").value(10))
                .andExpect(jsonPath("$.correlationId").value("corr-id"));
    }
}
