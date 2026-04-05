package br.com.techbr.fiscalanalyzer.sped.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthRequestContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedFiscalFileDetailResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedFiscalFileSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationDivergenceResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedReconciliationItemResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedCodeStatsResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedApuracaoSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedSummaryResponse;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedUploadResponse;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileSourceType;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.model.SpedReconciliationStatus;
import br.com.techbr.fiscalanalyzer.sped.service.SpedFiscalIngestionService;
import br.com.techbr.fiscalanalyzer.sped.service.SpedFiscalReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpedFiscalControllerTest {

    private MockMvc mockMvc;
    private SpedFiscalIngestionService spedFiscalIngestionService;
    private SpedFiscalReadService spedFiscalReadService;

    @BeforeEach
    void setUp() {
        spedFiscalIngestionService = mock(SpedFiscalIngestionService.class);
        spedFiscalReadService = mock(SpedFiscalReadService.class);
        UserAuthorizationService userAuthorizationService = mock(UserAuthorizationService.class);

        SpedFiscalController controller = new SpedFiscalController(
                spedFiscalIngestionService,
                spedFiscalReadService,
                userAuthorizationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void upload_sped_txt() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sped.txt", "text/plain", "|0000|...".getBytes());

        when(spedFiscalIngestionService.upload(eq(1L), eq(2L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SpedUploadResponse("TXT", 1, 1, 0, 1));

        mockMvc.perform(multipart("/sped/files/upload")
                        .file(file)
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputType").value("TXT"))
                .andExpect(jsonPath("$.acceptedFiles").value(1))
                .andExpect(jsonPath("$.queuedFiles").value(1));
    }

    @Test
    void list_sped_files() throws Exception {
        var content = new SpedFiscalFileSummaryResponse(
                10L,
                SpedFiscalFileStatus.PROCESSADO,
                SpedFiscalFileSourceType.TXT,
                "sped.txt",
                "hash",
                "020",
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-04T10:00:00Z"),
                Instant.parse("2026-04-04T10:00:01Z")
        );
        when(spedFiscalReadService.list(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(content), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/sped/files")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].status").value("PROCESSADO"));
    }

    @Test
    void reprocess_sped_file() throws Exception {
        when(spedFiscalReadService.reprocess(eq(10L), eq(1L), eq(2L), any()))
                .thenReturn(new SpedFiscalFileDetailResponse(
                        10L, 1L, 2L,
                        SpedFiscalFileStatus.PENDENTE_PARSE,
                        SpedFiscalFileSourceType.TXT,
                        "sped.txt",
                        "sped/1/2/hash.txt",
                        null,
                        "hash",
                        100L,
                        "text/plain",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-04T10:00:00Z"),
                        Instant.parse("2026-04-04T10:00:01Z")
                ));

        mockMvc.perform(post("/sped/files/10/reprocess")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDENTE_PARSE"));
    }

    @Test
    void summary_sped() throws Exception {
        when(spedFiscalReadService.summary(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(new SpedSummaryResponse(
                        1L,
                        2L,
                        null,
                        null,
                        7L,
                        List.of()
                ));

        mockMvc.perform(get("/sped/summary")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArquivos").value(7));
    }

    @Test
    void reconciliation_sped() throws Exception {
        when(spedFiscalReadService.reconciliation(eq(10L), eq(1L), eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(
                        new SpedReconciliationItemResponse(
                                900L,
                                21,
                                "35191111111111111111550010000000011000000010",
                                LocalDate.parse("2026-01-10"),
                                "55",
                                "123",
                                "1",
                                new java.math.BigDecimal("100.00"),
                                new java.math.BigDecimal("100.00"),
                                new java.math.BigDecimal("12.00"),
                                new java.math.BigDecimal("12.00"),
                                new java.math.BigDecimal("1.65"),
                                new java.math.BigDecimal("1.65"),
                                new java.math.BigDecimal("7.60"),
                                new java.math.BigDecimal("7.60"),
                                3L,
                                3L,
                                null,
                                SpedReconciliationStatus.OK
                        )
                ), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/sped/files/10/reconciliation")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].spedC100Id").value(900))
                .andExpect(jsonPath("$.content[0].status").value("OK"));
    }

    @Test
    void reconciliation_summary_sped() throws Exception {
        when(spedFiscalReadService.reconciliationSummary(eq(1L), eq(2L), eq(LocalDate.parse("2026-01-01")), eq(LocalDate.parse("2026-01-31")), eq(20), any()))
                .thenReturn(new SpedReconciliationSummaryResponse(
                        1L,
                        2L,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-01-31"),
                        3L,
                        100L,
                        80L,
                        10L,
                        5L,
                        3L,
                        1L,
                        1L,
                        List.of(new SpedCodeStatsResponse("5102", 10L, new java.math.BigDecimal("1500.00"))),
                        List.of(new SpedCodeStatsResponse("060", 7L, new java.math.BigDecimal("700.00"))),
                        List.of(new SpedCodeStatsResponse("5102", 8L, new java.math.BigDecimal("1200.00"))),
                        List.of(new SpedCodeStatsResponse("84713012", 5L, new java.math.BigDecimal("900.00"))),
                        new SpedApuracaoSummaryResponse(
                                2L,
                                new java.math.BigDecimal("1000.00"),
                                new java.math.BigDecimal("800.00"),
                                new java.math.BigDecimal("200.00"),
                                new java.math.BigDecimal("30.00"),
                                List.of()
                        )
                ));

        mockMvc.perform(get("/sped/reconciliation/summary")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("periodoInicio", "2026-01-01")
                        .param("periodoFim", "2026-01-31")
                        .param("limit", "20")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocumentosSped").value(100))
                .andExpect(jsonPath("$.totalOk").value(80))
                .andExpect(jsonPath("$.cfopSpedTop[0].codigo").value("5102"));
    }

    @Test
    void reconciliation_divergences_sped() throws Exception {
        when(spedFiscalReadService.reconciliationDivergences(
                eq(1L),
                eq(2L),
                eq(LocalDate.parse("2026-01-01")),
                eq(LocalDate.parse("2026-01-31")),
                eq(SpedReconciliationStatus.FALTANTE_XML),
                eq("35191111111111111111550010000000011000000010"),
                any(),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(new SpedReconciliationDivergenceResponse(
                        5L,
                        900L,
                        21,
                        "35191111111111111111550010000000011000000010",
                        LocalDate.parse("2026-01-10"),
                        "55",
                        "123",
                        "1",
                        new java.math.BigDecimal("100.00"),
                        null,
                        new java.math.BigDecimal("12.00"),
                        null,
                        new java.math.BigDecimal("1.65"),
                        null,
                        new java.math.BigDecimal("7.60"),
                        null,
                        3L,
                        null,
                        "xml_nao_encontrado",
                        SpedReconciliationStatus.FALTANTE_XML
                )),
                PageRequest.of(0, 50),
                1
        ));

        mockMvc.perform(get("/sped/reconciliation/divergences")
                        .param("tenantId", "1")
                        .param("empresaId", "2")
                        .param("periodoInicio", "2026-01-01")
                        .param("periodoFim", "2026-01-31")
                        .param("status", "FALTANTE_XML")
                        .param("accessKey", "35191111111111111111550010000000011000000010")
                        .requestAttr(UserAuthRequestContext.REQUEST_ATTRIBUTE,
                                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].spedFileId").value(5))
                .andExpect(jsonPath("$.content[0].status").value("FALTANTE_XML"));
    }
}
