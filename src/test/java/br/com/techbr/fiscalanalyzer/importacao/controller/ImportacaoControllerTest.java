package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportacaoControllerTest {

    private MockMvc mockMvc;
    private ImportacaoService importacaoService;

    @BeforeEach
    void setUp() {
        importacaoService = mock(ImportacaoService.class);
        ImportacaoController controller = new ImportacaoController(importacaoService);
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
                        .param("empresaId", "2"))
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
                        .param("empresaId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
