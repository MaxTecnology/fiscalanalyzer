package br.com.techbr.fiscalanalyzer.importacao.controller;

import br.com.techbr.fiscalanalyzer.common.exception.ApiExceptionHandler;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoDetailResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportItemResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportItemStatusCountResponse;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.service.ImportacaoReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportacaoReadControllerTest {

    private MockMvc mockMvc;
    private ImportacaoReadService readService;

    @BeforeEach
    void setUp() {
        readService = mock(ImportacaoReadService.class);
        ImportacaoReadController controller = new ImportacaoReadController(readService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void get_imports_id() throws Exception {
        ImportacaoDetailResponse response = new ImportacaoDetailResponse(
                1L,
                "EXTRAIDO",
                Instant.now(),
                Instant.now(),
                "a.zip",
                10L,
                "application/zip",
                "hash",
                null,
                null,
                2,
                List.of(new ImportItemStatusCountResponse("PARSEADO", 2))
        );
        when(readService.getDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/imports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("EXTRAIDO"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void get_imports_items() throws Exception {
        ImportItemResponse item = new ImportItemResponse(
                10L,
                "PARSEADO",
                "a.xml",
                100L,
                "hash",
                "35191111111111111111550010000000011000000010",
                (short) 55,
                LocalDate.of(2024, 1, 2),
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        Page<ImportItemResponse> page = new PageImpl<>(List.of(item), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(readService.listItems(eq(1L), eq(ImportItemStatus.PARSEADO), any())).thenReturn(page);

        mockMvc.perform(get("/imports/1/items")
                        .param("status", "PARSEADO")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].status").value("PARSEADO"));
    }
}
