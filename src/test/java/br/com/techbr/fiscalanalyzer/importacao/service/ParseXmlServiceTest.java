package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentAdditionalInfo;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentDuplicate;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentPayment;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentRegistry;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentAdditionalInfoRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentDuplicateRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentPaymentRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRegistryRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.item.model.FiscalItem;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParseXmlServiceTest {

    @Mock
    private ImportacaoRepository importacaoRepository;
    @Mock
    private ImportItemRepository importItemRepository;
    @Mock
    private FiscalDocumentRepository fiscalDocumentRepository;
    @Mock
    private FiscalDocumentRegistryRepository registryRepository;
    @Mock
    private FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository;
    @Mock
    private FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository;
    @Mock
    private FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository;
    @Mock
    private FiscalItemRepository fiscalItemRepository;
    @Mock
    private StorageService storageService;

    @Test
    void process_sucesso_criaDocumentoERegistry() throws Exception {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 1L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);
        importacao.setStatus(ImportacaoStatus.EXTRAIDO);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 10L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(1L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(1L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());

        AtomicLong docId = new AtomicLong(100L);
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> {
            FiscalDocument doc = i.getArgument(0);
            ReflectionTestUtils.setField(doc, "id", docId.getAndIncrement());
            return doc;
        });
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(i -> i.getArgument(0));

        byte[] zip = buildZipWithXml("a.xml", minimalNfeXml());
        when(storageService.get("imports/1/abc.zip")).thenReturn(new ByteArrayInputStream(zip));

        service.process(new ParseXmlMessage(1L, 10L, "fiscal-raw", "imports/1/abc.zip", "a.xml", "abc"), "corr");

        ArgumentCaptor<ImportItem> itemCaptor = ArgumentCaptor.forClass(ImportItem.class);
        verify(importItemRepository, atLeastOnce()).save(itemCaptor.capture());
        ImportItem last = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertEquals(ImportItemStatus.PARSEADO, last.getStatus());
        assertEquals("35191111111111111111550010000000011000000010", last.getAccessKey());
        assertEquals(LocalDate.of(2024, 1, 2), last.getIssueDate());
        assertEquals(0, last.getXmlHash().length() % 2);
        assertNotNull(last.getXmlSize());
        assertTrue(last.getXmlSize() > 0L);

        verify(registryRepository, atLeastOnce()).save(any(FiscalDocumentRegistry.class));
    }

    @Test
    void process_preencheTotaisEItens() throws Exception {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 7L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 17L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(17L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(7L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(7L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalItemRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        byte[] zip = buildZipWithXml("a.xml", nfeXmlWithTotalsAndItem());
        when(storageService.get("imports/7/abc.zip")).thenReturn(new ByteArrayInputStream(zip));

        service.process(new ParseXmlMessage(7L, 17L, "fiscal-raw", "imports/7/abc.zip", "a.xml", "abc"), "corr");

        ArgumentCaptor<FiscalDocument> docCaptor = ArgumentCaptor.forClass(FiscalDocument.class);
        verify(fiscalDocumentRepository, atLeastOnce()).save(docCaptor.capture());
        FiscalDocument savedDoc = docCaptor.getValue();
        assertEquals("85834.49", savedDoc.getTotalProducts().toString());
        assertEquals("10300.14", savedDoc.getTotalIcms().toString());
        assertEquals("1246.32", savedDoc.getTotalPis().toString());
        assertEquals("5740.61", savedDoc.getTotalCofins().toString());

        ArgumentCaptor<List<FiscalItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(fiscalItemRepository).saveAll(itemCaptor.capture());
        List<FiscalItem> items = itemCaptor.getValue();
        assertEquals(1, items.size());
        FiscalItem savedItem = items.get(0);
        assertEquals(1, savedItem.getItemNumber());
        assertEquals("0001", savedItem.getProductCode());
        assertEquals("PRODUTO A", savedItem.getProductDescription());
        assertEquals("48025610", savedItem.getNcm());
        assertEquals("6102", savedItem.getCfop());
        assertEquals("00", savedItem.getCstIcms());
        assertEquals("100.00", savedItem.getIcmsBase().toString());
        assertEquals("12.00", savedItem.getIcmsRate().toString());
        assertEquals("12.00", savedItem.getIcmsValue().toString());
        assertEquals("100.00", savedItem.getPisBase().toString());
        assertEquals("1.65", savedItem.getPisRate().toString());
        assertEquals("1.65", savedItem.getPisValue().toString());
        assertEquals("100.00", savedItem.getCofinsBase().toString());
        assertEquals("7.60", savedItem.getCofinsRate().toString());
        assertEquals("7.60", savedItem.getCofinsValue().toString());
    }

    @Test
    void process_duplicado_marcaDuplicado_naoCriaDocumento() throws Exception {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 2L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 11L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(11L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(2L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(2L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any()))
                .thenReturn(Optional.of(new FiscalDocumentRegistry()));

        byte[] zip = buildZipWithXml("a.xml", minimalNfeXml());
        when(storageService.get("imports/2/abc.zip")).thenReturn(new ByteArrayInputStream(zip));

        service.process(new ParseXmlMessage(2L, 11L, "fiscal-raw", "imports/2/abc.zip", "a.xml", "abc"), "corr");

        verify(fiscalDocumentRepository, never()).save(any(FiscalDocument.class));
        ArgumentCaptor<ImportItem> itemCaptor = ArgumentCaptor.forClass(ImportItem.class);
        verify(importItemRepository, atLeastOnce()).save(itemCaptor.capture());
        ImportItem last = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertEquals(ImportItemStatus.DUPLICADO, last.getStatus());
    }

    @Test
    void process_namespace_xml_sucesso() throws Exception {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 5L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 15L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(15L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(5L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(5L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));

        byte[] zip = buildZipWithXml("a.xml", minimalNfeXmlWithNamespace());
        when(storageService.get("imports/5/abc.zip")).thenReturn(new ByteArrayInputStream(zip));

        service.process(new ParseXmlMessage(5L, 15L, "fiscal-raw", "imports/5/abc.zip", "a.xml", "abc"), "corr");

        ArgumentCaptor<ImportItem> itemCaptor = ArgumentCaptor.forClass(ImportItem.class);
        verify(importItemRepository, atLeastOnce()).save(itemCaptor.capture());
        ImportItem last = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertEquals(ImportItemStatus.PARSEADO, last.getStatus());
    }

    @Test
    void process_manifest_objetoDireto_sucesso() {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 8L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 18L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);
        item.setXmlPath("99/99/a.xml");
        item.setStorageObjectKey("99/99/a.xml");

        when(importItemRepository.findById(18L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(8L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(8L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));

        when(storageService.get("99/99/a.xml"))
                .thenReturn(new ByteArrayInputStream(minimalNfeXmlWithNamespace().getBytes(StandardCharsets.UTF_8)));

        service.process(new ParseXmlMessage(8L, 18L, "fiscal-raw", "99/99/a.xml", null, "abc"), "corr");

        ArgumentCaptor<FiscalDocument> docCaptor = ArgumentCaptor.forClass(FiscalDocument.class);
        verify(fiscalDocumentRepository).save(docCaptor.capture());
        FiscalDocument savedDoc = docCaptor.getValue();
        assertEquals("99/99/a.xml", savedDoc.getXmlPath());

        ArgumentCaptor<ImportItem> itemCaptor = ArgumentCaptor.forClass(ImportItem.class);
        verify(importItemRepository, atLeastOnce()).save(itemCaptor.capture());
        ImportItem last = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertEquals(ImportItemStatus.PARSEADO, last.getStatus());
        assertEquals("35191111111111111111550010000000011000000010", last.getAccessKey());
        assertNotNull(last.getXmlSize());
        assertTrue(last.getXmlSize() > 0L);
    }

    @Test
    void process_nfce_com_destinatarioCpf_persisteDocumento() {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 9L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 19L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);
        item.setXmlPath("99/99/nfce.xml");
        item.setStorageObjectKey("99/99/nfce.xml");

        when(importItemRepository.findById(19L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(9L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(9L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(storageService.get("99/99/nfce.xml"))
                .thenReturn(new ByteArrayInputStream(nfceWithDestCpf().getBytes(StandardCharsets.UTF_8)));

        service.process(new ParseXmlMessage(9L, 19L, "fiscal-raw", "99/99/nfce.xml", null, "abc"), "corr");

        ArgumentCaptor<FiscalDocument> docCaptor = ArgumentCaptor.forClass(FiscalDocument.class);
        verify(fiscalDocumentRepository).save(docCaptor.capture());
        FiscalDocument savedDoc = docCaptor.getValue();
        assertEquals((short) 65, savedDoc.getModel());
        assertEquals("23168800023", savedDoc.getDestCnpj());
        assertEquals("4.07", savedDoc.getTotalAmount().toString());
    }

    @Test
    void process_camposFase2_persistePagamentosDuplicatasEObservacoes() {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 21L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 121L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);
        item.setXmlPath("99/99/fase2.xml");
        item.setStorageObjectKey("99/99/fase2.xml");

        when(importItemRepository.findById(121L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(21L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(21L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentPaymentRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentDuplicateRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentAdditionalInfoRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(storageService.get("99/99/fase2.xml"))
                .thenReturn(new ByteArrayInputStream(nfeWithPhase2Groups().getBytes(StandardCharsets.UTF_8)));

        service.process(new ParseXmlMessage(21L, 121L, "fiscal-raw", "99/99/fase2.xml", null, "abc"), "corr");

        ArgumentCaptor<FiscalDocument> docCaptor = ArgumentCaptor.forClass(FiscalDocument.class);
        verify(fiscalDocumentRepository).save(docCaptor.capture());
        FiscalDocument savedDoc = docCaptor.getValue();
        assertEquals(Integer.valueOf(176272), savedDoc.getNumeroNota());
        assertEquals("1", savedDoc.getSerie());
        assertEquals("VENDA", savedDoc.getNaturezaOperacao());
        assertEquals((short) 1, savedDoc.getAmbiente());
        assertEquals((short) 1, savedDoc.getFinalidadeEmissao());
        assertEquals((short) 0, savedDoc.getConsumidorFinal());
        assertEquals((short) 3, savedDoc.getPresencaComprador());
        assertEquals("EMITENTE TESTE", savedDoc.getEmitNome());
        assertEquals("123456789", savedDoc.getEmitIe());
        assertEquals("PE", savedDoc.getEmitUf());
        assertEquals("12345678901", savedDoc.getDestDocumento());
        assertEquals("DESTINATARIO TESTE", savedDoc.getDestNome());
        assertEquals("88624.11", savedDoc.getTotalAmount().toString());
        assertEquals("100.00", savedDoc.getTotalFrete().toString());
        assertEquals("10.00", savedDoc.getTotalDesconto().toString());
        assertEquals("5.00", savedDoc.getTotalOutros().toString());
        assertEquals("2789.62", savedDoc.getTotalIpi().toString());
        assertEquals("126250139789272", savedDoc.getProtocoloNumero());
        assertEquals(Integer.valueOf(100), savedDoc.getProtocoloStatus());
        assertEquals("Autorizado o uso da NF-e", savedDoc.getProtocoloMotivo());
        assertEquals("0000176272", savedDoc.getFaturaNumero());
        assertEquals("88624.11", savedDoc.getFaturaValorOriginal().toString());
        assertEquals("0.00", savedDoc.getFaturaValorDesconto().toString());
        assertEquals("88624.11", savedDoc.getFaturaValorLiquido().toString());
        assertEquals("http://exemplo.local/consulta", savedDoc.getQrCodeUrl());

        ArgumentCaptor<List<FiscalDocumentPayment>> paymentCaptor = ArgumentCaptor.forClass(List.class);
        verify(fiscalDocumentPaymentRepository).saveAll(paymentCaptor.capture());
        assertEquals(1, paymentCaptor.getValue().size());
        assertEquals("03", paymentCaptor.getValue().getFirst().getPaymentType());
        assertEquals("88624.11", paymentCaptor.getValue().getFirst().getPaymentAmount().toString());

        ArgumentCaptor<List<FiscalDocumentDuplicate>> duplicateCaptor = ArgumentCaptor.forClass(List.class);
        verify(fiscalDocumentDuplicateRepository).saveAll(duplicateCaptor.capture());
        assertEquals(1, duplicateCaptor.getValue().size());
        assertEquals("001", duplicateCaptor.getValue().getFirst().getDuplicateNumber());
        assertEquals(LocalDate.of(2026, 1, 19), duplicateCaptor.getValue().getFirst().getDueDate());

        ArgumentCaptor<List<FiscalDocumentAdditionalInfo>> infoCaptor = ArgumentCaptor.forClass(List.class);
        verify(fiscalDocumentAdditionalInfoRepository).saveAll(infoCaptor.capture());
        assertEquals(2, infoCaptor.getValue().size());
    }

    @Test
    void process_xmlInvalido_marcaFalha() throws Exception {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 3L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 12L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(12L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(3L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(3L), any(EnumSet.class))).thenReturn(0L);

        byte[] zip = buildZipWithXml("a.xml", "<NFe><infNFe></infNFe></NFe>");
        when(storageService.get("imports/3/abc.zip")).thenReturn(new ByteArrayInputStream(zip));

        service.process(new ParseXmlMessage(3L, 12L, "fiscal-raw", "imports/3/abc.zip", "a.xml", "abc"), "corr");

        ArgumentCaptor<ImportItem> itemCaptor = ArgumentCaptor.forClass(ImportItem.class);
        verify(importItemRepository, atLeastOnce()).save(itemCaptor.capture());
        ImportItem last = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertEquals(ImportItemStatus.FALHA_PARSE, last.getStatus());
    }

    @Test
    void process_falhaMinio_lancaInfraException() {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 4L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 13L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(13L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(4L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(storageService.get("imports/4/abc.zip")).thenThrow(new RuntimeException("timeout"));

        assertThrows(InfraException.class, () ->
                service.process(new ParseXmlMessage(4L, 13L, "fiscal-raw", "imports/4/abc.zip", "a.xml", "abc"), "corr")
        );
    }

    @Test
    void process_race_registry_marcaDuplicado() throws Exception {
        ParseXmlService service = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 6L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 16L);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        when(importItemRepository.findById(16L)).thenReturn(Optional.of(item));
        when(importacaoRepository.findById(6L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(6L), any(EnumSet.class))).thenReturn(0L);
        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(eq(1L), eq(2L), any())).thenReturn(Optional.empty());
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(registryRepository.save(any(FiscalDocumentRegistry.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        byte[] zip = buildZipWithXml("a.xml", minimalNfeXml());
        when(storageService.get("imports/6/abc.zip")).thenReturn(new ByteArrayInputStream(zip));

        service.process(new ParseXmlMessage(6L, 16L, "fiscal-raw", "imports/6/abc.zip", "a.xml", "abc"), "corr");

        ArgumentCaptor<ImportItem> itemCaptor = ArgumentCaptor.forClass(ImportItem.class);
        verify(importItemRepository, atLeastOnce()).save(itemCaptor.capture());
        ImportItem last = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertEquals(ImportItemStatus.DUPLICADO, last.getStatus());
    }

    private byte[] buildZipWithXml(String name, String xml) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String minimalNfeXml() {
        return """
                <NFe>
                  <infNFe Id="NFe35191111111111111111550010000000011000000010">
                    <ide>
                      <mod>55</mod>
                      <tpNF>1</tpNF>
                      <dEmi>2024-01-02</dEmi>
                    </ide>
                    <emit><CNPJ>11111111111111</CNPJ></emit>
                    <dest><CNPJ>22222222222222</CNPJ></dest>
                    <total><ICMSTot><vNF>123.45</vNF></ICMSTot></total>
                  </infNFe>
                </NFe>
                """;
    }

    private String minimalNfeXmlWithNamespace() {
        return """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <infNFe Id="NFe35191111111111111111550010000000011000000010">
                    <ide>
                      <mod>55</mod>
                      <tpNF>1</tpNF>
                      <dEmi>2024-01-02</dEmi>
                    </ide>
                    <emit><CNPJ>11111111111111</CNPJ></emit>
                    <dest><CNPJ>22222222222222</CNPJ></dest>
                    <total><ICMSTot><vNF>123.45</vNF></ICMSTot></total>
                  </infNFe>
                </NFe>
                """;
    }

    private String nfeXmlWithTotalsAndItem() {
        return """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <infNFe Id="NFe26251216404287029056550010001762721022736522">
                    <ide>
                      <mod>55</mod>
                      <tpNF>1</tpNF>
                      <dhEmi>2025-12-19T10:53:06-03:00</dhEmi>
                    </ide>
                    <emit><CNPJ>16404287029056</CNPJ></emit>
                    <dest><CNPJ>05841177000134</CNPJ></dest>
                    <det nItem="1">
                      <prod>
                        <cProd>0001</cProd>
                        <xProd>PRODUTO A</xProd>
                        <NCM>48025610</NCM>
                        <CFOP>6102</CFOP>
                        <qCom>1.0000</qCom>
                        <vUnCom>100.0000</vUnCom>
                        <vProd>100.00</vProd>
                      </prod>
                      <imposto>
                        <ICMS>
                          <ICMS00>
                            <CST>00</CST>
                            <vBC>100.00</vBC>
                            <pICMS>12.00</pICMS>
                            <vICMS>12.00</vICMS>
                          </ICMS00>
                        </ICMS>
                        <PIS>
                          <PISAliq>
                            <vBC>100.00</vBC>
                            <pPIS>1.65</pPIS>
                            <vPIS>1.65</vPIS>
                          </PISAliq>
                        </PIS>
                        <COFINS>
                          <COFINSAliq>
                            <vBC>100.00</vBC>
                            <pCOFINS>7.60</pCOFINS>
                            <vCOFINS>7.60</vCOFINS>
                          </COFINSAliq>
                        </COFINS>
                      </imposto>
                    </det>
                    <total>
                      <ICMSTot>
                        <vProd>85834.49</vProd>
                        <vICMS>10300.14</vICMS>
                        <vPIS>1246.32</vPIS>
                        <vCOFINS>5740.61</vCOFINS>
                        <vNF>88624.11</vNF>
                      </ICMSTot>
                    </total>
                  </infNFe>
                </NFe>
                """;
    }

    private String nfceWithDestCpf() {
        return """
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
                  <NFe>
                    <infNFe Id="NFe26251052729931000390650060000031721472939584">
                      <ide>
                        <mod>65</mod>
                        <tpNF>1</tpNF>
                        <dhEmi>2025-10-01T08:18:08-03:00</dhEmi>
                      </ide>
                      <emit><CNPJ>52729931000390</CNPJ></emit>
                      <dest><CPF>23168800023</CPF></dest>
                      <total><ICMSTot><vNF>4.07</vNF></ICMSTot></total>
                    </infNFe>
                  </NFe>
                </nfeProc>
                """;
    }

    private String nfeWithPhase2Groups() {
        return """
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
                  <NFe>
                    <infNFe Id="NFe26251216404287029056550010001762721022736522">
                      <ide>
                        <natOp>VENDA</natOp>
                        <mod>55</mod>
                        <serie>1</serie>
                        <nNF>176272</nNF>
                        <dhEmi>2025-12-19T10:53:06-03:00</dhEmi>
                        <tpNF>1</tpNF>
                        <tpAmb>1</tpAmb>
                        <finNFe>1</finNFe>
                        <indFinal>0</indFinal>
                        <indPres>3</indPres>
                      </ide>
                      <emit>
                        <CNPJ>16404287029056</CNPJ>
                        <xNome>EMITENTE TESTE</xNome>
                        <IE>123456789</IE>
                        <enderEmit><UF>PE</UF></enderEmit>
                      </emit>
                      <dest>
                        <CPF>12345678901</CPF>
                        <xNome>DESTINATARIO TESTE</xNome>
                        <IE>987654321</IE>
                        <enderDest><UF>AL</UF></enderDest>
                      </dest>
                      <total>
                        <ICMSTot>
                          <vProd>85834.49</vProd>
                          <vFrete>100.00</vFrete>
                          <vDesc>10.00</vDesc>
                          <vOutro>5.00</vOutro>
                          <vIPI>2789.62</vIPI>
                          <vPIS>1246.32</vPIS>
                          <vCOFINS>5740.61</vCOFINS>
                          <vICMS>10300.14</vICMS>
                          <vNF>88624.11</vNF>
                          <vTotTrib>999.99</vTotTrib>
                        </ICMSTot>
                      </total>
                      <pag>
                        <detPag>
                          <indPag>1</indPag>
                          <tPag>03</tPag>
                          <vPag>88624.11</vPag>
                          <card>
                            <tpIntegra>1</tpIntegra>
                            <CNPJ>01425787000104</CNPJ>
                            <tBand>01</tBand>
                            <cAut>AUT123</cAut>
                          </card>
                        </detPag>
                      </pag>
                      <cobr>
                        <fat>
                          <nFat>0000176272</nFat>
                          <vOrig>88624.11</vOrig>
                          <vDesc>0.00</vDesc>
                          <vLiq>88624.11</vLiq>
                        </fat>
                        <dup>
                          <nDup>001</nDup>
                          <dVenc>2026-01-19</dVenc>
                          <vDup>29541.96</vDup>
                        </dup>
                      </cobr>
                      <infAdic>
                        <infCpl>INFO COMPLEMENTAR</infCpl>
                        <obsCont xCampo="OBSCONT"><xTexto>OBS TESTE</xTexto></obsCont>
                      </infAdic>
                    </infNFe>
                    <infNFeSupl><qrCode>http://exemplo.local/consulta</qrCode></infNFeSupl>
                  </NFe>
                  <protNFe>
                    <infProt>
                      <chNFe>26251216404287029056550010001762721022736522</chNFe>
                      <dhRecbto>2025-12-19T10:53:09-03:00</dhRecbto>
                      <nProt>126250139789272</nProt>
                      <cStat>100</cStat>
                      <xMotivo>Autorizado o uso da NF-e</xMotivo>
                    </infProt>
                  </protNFe>
                </nfeProc>
                """;
    }
}
