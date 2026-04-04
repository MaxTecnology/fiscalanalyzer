package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentExportFormat;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentExportRequest;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentEventRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.item.model.FiscalItem;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {

    @Mock
    private FiscalDocumentRepository fiscalDocumentRepository;

    @Mock
    private FiscalItemRepository fiscalItemRepository;

    @Mock
    private FiscalDocumentEventRepository fiscalDocumentEventRepository;

    @Mock
    private TenantEmpresaValidationService tenantEmpresaValidationService;

    @Mock
    private StorageService storageService;

    @Mock
    private DanfeGeneratorService danfeGeneratorService;

    @InjectMocks
    private DocumentQueryService service;

    @Test
    void findByAccessKey_retornaDocumentoComItens() {
        FiscalDocument document = buildDocument();
        FiscalItem item = new FiscalItem();
        item.setItemNumber(1);
        item.setProductDescription("Produto teste");
        item.setCfop("5102");

        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(99L, 42L, document.getAccessKey()))
                .thenReturn(Optional.of(document));
        when(fiscalItemRepository.findByDocumentIdOrderByItemNumberAsc(100L)).thenReturn(List.of(item));

        var response = service.findByAccessKey(99L, 42L, document.getAccessKey());

        assertEquals(document.getAccessKey(), response.accessKey());
        assertEquals("EMPRESA EMITENTE", response.emitRazaoSocial());
        assertEquals(1, response.items().size());
        assertEquals("Produto teste", response.items().get(0).descricao());
    }

    @Test
    void listDocuments_retornaPagina() {
        FiscalDocument document = buildDocument();
        when(fiscalDocumentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        var result = service.listDocuments(
                1L,
                2L,
                (short) 55,
                "s",
                FiscalDocumentStatus.ATIVA,
                "11.111.111/1111-11",
                "22.222.222/2222-22",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                10L,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("35191111111111111111550010000000011000000010", result.getContent().get(0).accessKey());
    }

    @Test
    void downloadXml_manifest_lerObjetoDireto() {
        FiscalDocument document = buildDocument();
        Importacao importacao = new Importacao();
        importacao.setSourceType(ImportacaoSourceType.MANIFEST);
        document.setImportacao(importacao);

        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(1L, 2L, document.getAccessKey()))
                .thenReturn(Optional.of(document));
        when(storageService.get("1/2/doc.xml"))
                .thenReturn(new ByteArrayInputStream("<NFe/>".getBytes()));

        var xml = service.downloadXml(1L, 2L, document.getAccessKey());

        assertEquals("35191111111111111111550010000000011000000010.xml", xml.fileName());
        assertEquals("<NFe/>", new String(xml.payload()));
    }

    @Test
    void downloadDanfe_manifest_geraPdf() {
        FiscalDocument document = buildDocument();
        Importacao importacao = new Importacao();
        importacao.setSourceType(ImportacaoSourceType.MANIFEST);
        document.setImportacao(importacao);

        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(1L, 2L, document.getAccessKey()))
                .thenReturn(Optional.of(document));
        when(storageService.get("1/2/doc.xml"))
                .thenReturn(new ByteArrayInputStream("<NFe/>".getBytes()));
        when(danfeGeneratorService.generatePdf("<NFe/>", (short) 55, null))
                .thenReturn("%PDF-1.4".getBytes());

        var danfe = service.downloadDanfe(1L, 2L, document.getAccessKey());

        assertEquals("35191111111111111111550010000000011000000010-danfe.pdf", danfe.fileName());
        assertEquals("application/pdf", danfe.contentType());
        assertEquals("%PDF-1.4", new String(danfe.payload()));
    }

    @Test
    void summarize_retornaAgregados() {
        when(fiscalDocumentRepository.countByTenantIdAndEmpresaIdAndIssueDateBetween(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))).thenReturn(100L);
        when(fiscalDocumentRepository.countByTenantIdAndEmpresaIdAndIssueDateBetweenAndModel(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), (short) 55)).thenReturn(90L);
        when(fiscalDocumentRepository.countByTenantIdAndEmpresaIdAndIssueDateBetweenAndModel(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), (short) 65)).thenReturn(10L);
        when(fiscalDocumentRepository.countByTenantIdAndEmpresaIdAndIssueDateBetweenAndStatusDocumento(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), FiscalDocumentStatus.CANCELADA)).thenReturn(3L);
        when(fiscalDocumentRepository.sumTotalAmountByOperationType(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "E")).thenReturn(new BigDecimal("1000.00"));
        when(fiscalDocumentRepository.sumTotalAmountByOperationType(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "S")).thenReturn(new BigDecimal("3000.00"));
        when(fiscalDocumentRepository.sumTotalIcmsByOperationType(1L, 2L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "S")).thenReturn(new BigDecimal("360.00"));

        var summary = service.summarize(1L, 2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertEquals(100L, summary.totalDocumentos());
        assertEquals(new BigDecimal("3000.00"), summary.valorTotalSaidas());
    }

    @Test
    void exportDocuments_pdf_retornaArquivo() {
        FiscalDocument document = buildDocument();
        when(fiscalDocumentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 1), 1));

        var request = new DocumentExportRequest(
                1L,
                2L,
                (short) 55,
                "s",
                FiscalDocumentStatus.ATIVA,
                "11.111.111/1111-11",
                "22.222.222/2222-22",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                10L,
                DocumentExportFormat.PDF
        );

        var file = service.exportDocuments(request);

        assertEquals("application/pdf", file.contentType());
        assertTrue(file.fileName().endsWith(".pdf"));
        assertTrue(new String(file.payload()).startsWith("%PDF-1.4"));
    }

    @Test
    void exportDocuments_csv_padrao_quandoFormatoNulo() {
        FiscalDocument document = buildDocument();
        when(fiscalDocumentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 1), 1));

        var request = new DocumentExportRequest(
                1L,
                2L,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                null,
                null
        );

        var file = service.exportDocuments(request);

        assertEquals("text/csv", file.contentType());
        assertTrue(file.fileName().endsWith(".csv"));
        assertTrue(new String(file.payload()).contains("access_key;serie;numero"));
    }

    @Test
    void findByAccessKey_retorna422_quandoTenantEmpresaInvalidos() {
        doThrow(new UnprocessableEntityException("empresa nao encontrada"))
                .when(tenantEmpresaValidationService).validateAtivo(99L, 42L);

        assertThrows(UnprocessableEntityException.class,
                () -> service.findByAccessKey(99L, 42L, "35191111111111111111550010000000011000000010"));
        verifyNoInteractions(fiscalDocumentRepository);
    }

    @Test
    void listDocuments_lancaErro_quandoPeriodoInvalido() {
        assertThrows(ValidationException.class,
                () -> service.listDocuments(
                        1L,
                        2L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 4, 2),
                        LocalDate.of(2026, 4, 1),
                        null,
                        PageRequest.of(0, 20)
                ));
    }

    private FiscalDocument buildDocument() {
        FiscalDocument document = new FiscalDocument();
        document.setTenantId(99L);
        document.setEmpresaId(42L);
        document.setModel((short) 55);
        document.setAccessKey("35191111111111111111550010000000011000000010");
        document.setIssueDate(LocalDate.of(2024, 1, 2));
        document.setOperationType("S");
        document.setStatusDocumento(FiscalDocumentStatus.ATIVA);
        document.setEmitCnpj("11111111111111");
        document.setEmitNome("EMPRESA EMITENTE");
        document.setDestCnpj("22222222222222");
        document.setDestNome("EMPRESA DESTINO");
        document.setTotalAmount(new BigDecimal("123.45"));
        document.setXmlPath("1/2/doc.xml");
        document.setXmlHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        Importacao importacao = new Importacao();
        importacao.setSourceType(ImportacaoSourceType.MANIFEST);
        importacao.setArquivoPath("1/2/file.zip");
        document.setImportacao(importacao);

        try {
            var field = FiscalDocument.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(document, 100L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return document;
    }
}
