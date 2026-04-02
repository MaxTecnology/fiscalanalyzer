package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageBackfillResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentResponse;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentCoverageService;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private MockMvc mockMvc;
    private DocumentQueryService service;
    private DocumentCoverageService coverageService;
    private UserAuthorizationService userAuthorizationService;

    @BeforeEach
    void setUp() {
        service = mock(DocumentQueryService.class);
        coverageService = mock(DocumentCoverageService.class);
        userAuthorizationService = mock(UserAuthorizationService.class);
        DocumentController controller = new DocumentController(service, coverageService, userAuthorizationService);
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
                "hash",
                "ATIVA",
                null,
                null,
                null
        );
        when(service.findByAccessKey(1L, 2L, "35191111111111111111550010000000011000000010")).thenReturn(response);

        mockMvc.perform(get("/documents/35191111111111111111550010000000011000000010")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessKey").value("35191111111111111111550010000000011000000010"))
                .andExpect(jsonPath("$.model").value(55));
    }

    @Test
    void get_document_coverage_summary() throws Exception {
        when(coverageService.summarize(1L, 2L)).thenReturn(
                new DocumentCoverageSummaryResponse(1L, 2L, 10, 2, 1, 1, 3, 2, 4, 5)
        );

        mockMvc.perform(get("/documents/coverage")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(10))
                .andExpect(jsonPath("$.missingIdeCoverage").value(2));
    }

    @Test
    void post_document_coverage_backfill() throws Exception {
        when(coverageService.backfill(1L, 2L, 250)).thenReturn(
                new DocumentCoverageBackfillResponse(1L, 2L, 250, 250, 200, 30, 20)
        );

        mockMvc.perform(post("/documents/coverage/backfill")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("limit", "250")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedItems").value(250))
                .andExpect(jsonPath("$.updatedDocuments").value(200));
    }
}
