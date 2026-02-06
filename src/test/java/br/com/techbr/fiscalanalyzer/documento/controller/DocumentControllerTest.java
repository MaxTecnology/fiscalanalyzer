package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentResponse;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private MockMvc mockMvc;
    private DocumentQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(DocumentQueryService.class);
        DocumentController controller = new DocumentController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void get_document_by_access_key() throws Exception {
        FiscalDocumentResponse response = new FiscalDocumentResponse(
                (short) 55,
                "35191111111111111111550010000000011000000010",
                LocalDate.of(2024, 1, 2),
                "S",
                "11111111111111",
                "22222222222222",
                new BigDecimal("123.45"),
                1L,
                "a.xml",
                "hash"
        );
        when(service.findByAccessKey(1L, 2L, "35191111111111111111550010000000011000000010")).thenReturn(response);

        mockMvc.perform(get("/documents/35191111111111111111550010000000011000000010")
                        .param("tenantId", "1")
                        .param("empresaId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessKey").value("35191111111111111111550010000000011000000010"))
                .andExpect(jsonPath("$.model").value(55));
    }
}
