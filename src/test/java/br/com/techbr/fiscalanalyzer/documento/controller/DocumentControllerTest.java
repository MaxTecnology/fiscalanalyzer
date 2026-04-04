package br.com.techbr.fiscalanalyzer.documento.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCodeStatsResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageBackfillResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentExportFormat;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentListItemResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentPartyResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentDetailResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentEventResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentItemDetailResponse;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentCoverageService;
import br.com.techbr.fiscalanalyzer.documento.service.DocumentQueryService;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private MockMvc mockMvc;
    private DocumentQueryService queryService;
    private DocumentCoverageService coverageService;

    @BeforeEach
    void setUp() {
        queryService = mock(DocumentQueryService.class);
        coverageService = mock(DocumentCoverageService.class);
        UserAuthorizationService userAuthorizationService = mock(UserAuthorizationService.class);

        DocumentController controller = new DocumentController(queryService, coverageService, userAuthorizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void list_documents() throws Exception {
        var response = new DocumentListItemResponse(
                "35191111111111111111550010000000011000000010",
                (short) 55,
                "001",
                123,
                LocalDate.of(2024, 1, 2),
                "S",
                "ATIVA",
                "11111111111111",
                "EMPRESA A",
                "22222222222222",
                "EMPRESA B",
                new BigDecimal("123.45"),
                10L
        );

        when(queryService.listDocuments(eq(1L), eq(2L), eq((short) 55), eq("S"), eq(FiscalDocumentStatus.ATIVA),
                eq("11111111111111"), eq("22222222222222"), eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2024, 1, 31)), eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/documents")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("model", "55")
                        .param("operationType", "S")
                        .param("statusDocumento", "ATIVA")
                        .param("emitCnpj", "11111111111111")
                        .param("destCnpj", "22222222222222")
                        .param("issueDateFrom", "2024-01-01")
                        .param("issueDateTo", "2024-01-31")
                        .param("importacaoId", "10")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accessKey").value("35191111111111111111550010000000011000000010"))
                .andExpect(jsonPath("$.content[0].nfeNumero").value(123));
    }

    @Test
    void list_documents_invalid_status_retorna_400() throws Exception {
        mockMvc.perform(get("/documents")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("statusDocumento", "INVALIDO")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_documents_query_error_retorna_400() throws Exception {
        when(queryService.listDocuments(eq(1L), eq(2L), eq((short) 55), eq("S"), eq(FiscalDocumentStatus.ATIVA),
                eq(null), eq(null), eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 3, 31)), eq(null), any()))
                .thenThrow(new InvalidDataAccessApiUsageException("query invalida"));

        mockMvc.perform(get("/documents")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("model", "55")
                        .param("operationType", "S")
                        .param("statusDocumento", "ATIVA")
                        .param("issueDateFrom", "2026-03-01")
                        .param("issueDateTo", "2026-03-31")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Parametros de consulta invalidos"));
    }

    @Test
    void list_documents_query_resource_error_retorna_400() throws Exception {
        when(queryService.listDocuments(eq(1L), eq(2L), eq((short) 55), eq("S"), eq(FiscalDocumentStatus.ATIVA),
                eq(null), eq(null), eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 3, 31)), eq(null), any()))
                .thenThrow(new InvalidDataAccessResourceUsageException("sql grammar"));

        mockMvc.perform(get("/documents")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("model", "55")
                        .param("operationType", "S")
                        .param("statusDocumento", "ATIVA")
                        .param("issueDateFrom", "2026-03-01")
                        .param("issueDateTo", "2026-03-31")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Parametros de consulta invalidos"));
    }

    @Test
    void list_documents_query_jpa_semantic_error_retorna_400() throws Exception {
        when(queryService.listDocuments(eq(1L), eq(2L), eq((short) 55), eq("S"), eq(FiscalDocumentStatus.ATIVA),
                eq(null), eq(null), eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 3, 31)), eq(null), any()))
                .thenThrow(new JpaSystemException(new RuntimeException("Could not resolve attribute 'totalAmount'")));

        mockMvc.perform(get("/documents")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("model", "55")
                        .param("operationType", "S")
                        .param("statusDocumento", "ATIVA")
                        .param("issueDateFrom", "2026-03-01")
                        .param("issueDateTo", "2026-03-31")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Parametros de consulta invalidos"));
    }

    @Test
    void get_document_detail() throws Exception {
        when(queryService.findByAccessKey(1L, 2L, "35191111111111111111550010000000011000000010"))
                .thenReturn(buildDetail());

        mockMvc.perform(get("/documents/35191111111111111111550010000000011000000010")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessKey").value("35191111111111111111550010000000011000000010"))
                .andExpect(jsonPath("$.items[0].nItem").value(1));
    }

    @Test
    void download_document_xml() throws Exception {
        when(queryService.downloadXml(1L, 2L, "35191111111111111111550010000000011000000010"))
                .thenReturn(new DocumentQueryService.DocumentXmlDownload(
                        "<NFe/>".getBytes(),
                        "35191111111111111111550010000000011000000010.xml",
                        "application/xml"
                ));

        mockMvc.perform(get("/documents/35191111111111111111550010000000011000000010/xml")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/xml"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"35191111111111111111550010000000011000000010.xml\""));
    }

    @Test
    void download_document_danfe() throws Exception {
        when(queryService.downloadDanfe(1L, 2L, "35191111111111111111550010000000011000000010"))
                .thenReturn(new DocumentQueryService.DocumentDanfeDownload(
                        "%PDF-1.4".getBytes(),
                        "35191111111111111111550010000000011000000010-danfe.pdf",
                        "application/pdf"
                ));

        mockMvc.perform(get("/documents/35191111111111111111550010000000011000000010/danfe")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"35191111111111111111550010000000011000000010-danfe.pdf\""));
    }

    @Test
    void list_document_events() throws Exception {
        when(queryService.listEvents(1L, 2L, "35191111111111111111550010000000011000000010"))
                .thenReturn(List.of(new FiscalDocumentEventResponse(
                        "CANCELAMENTO",
                        "110111",
                        "Cancelamento homologado",
                        Instant.parse("2026-01-02T10:15:30Z"),
                        "135000000000001",
                        135,
                        "Evento homologado"
                )));

        mockMvc.perform(get("/documents/35191111111111111111550010000000011000000010/events")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CANCELAMENTO"));
    }

    @Test
    void get_document_summary() throws Exception {
        when(queryService.summarize(1L, 2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(new DocumentSummaryResponse(
                        100,
                        90,
                        10,
                        3,
                        new BigDecimal("1000.00"),
                        new BigDecimal("3000.00"),
                        new BigDecimal("360.00"),
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31)
                ));

        mockMvc.perform(get("/documents/summary")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("issueDateFrom", "2026-03-01")
                        .param("issueDateTo", "2026-03-31")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocumentos").value(100))
                .andExpect(jsonPath("$.valorTotalSaidas").value(3000.00));
    }

    @Test
    void list_emitters_and_receivers() throws Exception {
        when(queryService.listEmitters(1L, 2L, "TECH", 20))
                .thenReturn(List.of(new DocumentPartyResponse("11111111111111", "TECHBR LTDA")));
        when(queryService.listReceivers(1L, 2L, "SHOP", 20))
                .thenReturn(List.of(new DocumentPartyResponse("22222222222222", "SHOP LTDA")));

        mockMvc.perform(get("/documents/emitters")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("q", "TECH")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cnpj").value("11111111111111"));

        mockMvc.perform(get("/documents/receivers")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("q", "SHOP")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cnpj").value("22222222222222"));
    }

    @Test
    void list_stats_cfop_ncm() throws Exception {
        when(queryService.statsByCfop(1L, 2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 50))
                .thenReturn(List.of(new DocumentCodeStatsResponse("5102", 10L, new BigDecimal("500.00"))));
        when(queryService.statsByNcm(1L, 2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 50))
                .thenReturn(List.of(new DocumentCodeStatsResponse("22030000", 3L, new BigDecimal("90.00"))));

        mockMvc.perform(get("/documents/stats/cfop")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("issueDateFrom", "2026-03-01")
                        .param("issueDateTo", "2026-03-31")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("5102"));

        mockMvc.perform(get("/documents/stats/ncm")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("issueDateFrom", "2026-03-01")
                        .param("issueDateTo", "2026-03-31")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("22030000"));
    }

    @Test
    void export_documents_pdf() throws Exception {
        when(queryService.exportDocuments(any()))
                .thenReturn(new DocumentQueryService.DocumentExportFile(
                        "%PDF-1.4".getBytes(),
                        "documents-1-2-2026-04-04.pdf",
                        "application/pdf"
                ));

        mockMvc.perform(post("/documents/export")
                        .contentType("application/json")
                        .content("""
                                {
                                  "tenantId": 1,
                                  "empresaId": 2,
                                  "issueDateFrom": "2026-03-01",
                                  "issueDateTo": "2026-03-31",
                                  "format": "%s"
                                }
                                """.formatted(DocumentExportFormat.PDF.name()))
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"documents-1-2-2026-04-04.pdf\""));
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

    private FiscalDocumentDetailResponse buildDetail() {
        return new FiscalDocumentDetailResponse(
                (short) 55,
                "35191111111111111111550010000000011000000010",
                LocalDate.of(2024, 1, 2),
                "S",
                "ATIVA",
                "11111111111111",
                "EMPRESA A",
                "123",
                "PE",
                "RECIFE",
                "22222222222222",
                "EMPRESA B",
                "456",
                "AL",
                "MACEIO",
                123,
                "001",
                "VENDA",
                new BigDecimal("100.00"),
                new BigDecimal("123.45"),
                new BigDecimal("12.34"),
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                10L,
                "1/2/abc.xml",
                "abc",
                "12345",
                100,
                "Autorizado",
                Instant.parse("2024-01-02T13:00:00Z"),
                null,
                null,
                null,
                List.of(new FiscalDocumentItemDetailResponse(
                        1,
                        "PROD1",
                        "Produto A",
                        "22030000",
                        "5102",
                        "UN",
                        new BigDecimal("1.0000"),
                        new BigDecimal("123.4500"),
                        new BigDecimal("123.45"),
                        new BigDecimal("12.00"),
                        new BigDecimal("14.81"),
                        new BigDecimal("1.65"),
                        new BigDecimal("2.03"),
                        new BigDecimal("7.60"),
                        new BigDecimal("9.38")
                ))
        );
    }
}
