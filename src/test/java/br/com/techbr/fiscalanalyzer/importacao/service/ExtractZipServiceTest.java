package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractZipServiceTest {

    @Mock
    private ImportacaoRepository importacaoRepository;

    @Mock
    private ImportItemRepository importItemRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void process_criaItensEPublicaMensagens() throws Exception {
        ExtractZipService service = new ExtractZipService(
                importacaoRepository,
                importItemRepository,
                storageService,
                eventPublisher,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 1L);
        importacao.setStatus(ImportacaoStatus.RECEBIDO);

        when(importacaoRepository.findById(1L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(importItemRepository.findByImportacaoIdAndXmlPath(eq(1L), any())).thenReturn(Optional.empty());

        AtomicLong seq = new AtomicLong(10L);
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(invocation -> {
            ImportItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", seq.getAndIncrement());
            return item;
        });

        byte[] zipBytes = buildZip();
        when(storageService.get("imports/1/abc.zip")).thenReturn(new ByteArrayInputStream(zipBytes));

        ExtractZipMessage message = new ExtractZipMessage(
                1L,
                "fiscal-raw",
                "imports/1/abc.zip",
                "abc"
        );

        service.process(message, "corr-1");

        ArgumentCaptor<ParseXmlRequestedEvent> msgCaptor = ArgumentCaptor.forClass(ParseXmlRequestedEvent.class);
        verify(eventPublisher, atLeast(2)).publishEvent(msgCaptor.capture());
        assertEquals(2, msgCaptor.getAllValues().size());

        ArgumentCaptor<Importacao> importCaptor = ArgumentCaptor.forClass(Importacao.class);
        verify(importacaoRepository, atLeast(2)).save(importCaptor.capture());
        Importacao last = importCaptor.getAllValues().get(importCaptor.getAllValues().size() - 1);
        assertEquals(ImportacaoStatus.EXTRAIDO, last.getStatus());
        assertEquals(2, last.getTotalEncontrado());
    }

    @Test
    void process_idempotente_reenfileiraSomentePendentes() throws Exception {
        ExtractZipService service = new ExtractZipService(
                importacaoRepository,
                importItemRepository,
                storageService,
                eventPublisher,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 2L);
        importacao.setStatus(ImportacaoStatus.RECEBIDO);

        ImportItem pendente = new ImportItem();
        ReflectionTestUtils.setField(pendente, "id", 20L);
        pendente.setStatus(ImportItemStatus.PENDENTE_PARSE);

        ImportItem concluido = new ImportItem();
        ReflectionTestUtils.setField(concluido, "id", 21L);
        concluido.setStatus(ImportItemStatus.CONCLUIDO);

        when(importacaoRepository.findById(2L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(importItemRepository.findByImportacaoIdAndXmlPath(2L, "a.xml")).thenReturn(Optional.of(pendente));
        when(importItemRepository.findByImportacaoIdAndXmlPath(2L, "b.xml")).thenReturn(Optional.of(concluido));

        byte[] zipBytes = buildZipOnlyXml();
        when(storageService.get("imports/2/abc.zip")).thenReturn(new ByteArrayInputStream(zipBytes));

        ExtractZipMessage message = new ExtractZipMessage(
                2L,
                "fiscal-raw",
                "imports/2/abc.zip",
                "abc"
        );

        service.process(message, "corr-2");

        ArgumentCaptor<ParseXmlRequestedEvent> msgCaptor = ArgumentCaptor.forClass(ParseXmlRequestedEvent.class);
        verify(eventPublisher).publishEvent(msgCaptor.capture());
        assertEquals(1, msgCaptor.getAllValues().size());
        assertEquals(20L, msgCaptor.getValue().importItemId());

        verify(importItemRepository, never()).save(any(ImportItem.class));
    }

    @Test
    void process_duasVezes_naoDuplicaItens() throws Exception {
        ExtractZipService service = new ExtractZipService(
                importacaoRepository,
                importItemRepository,
                storageService,
                eventPublisher,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 6L);
        importacao.setStatus(ImportacaoStatus.RECEBIDO);

        when(importacaoRepository.findById(6L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        java.util.Map<String, ImportItem> store = new java.util.HashMap<>();
        AtomicLong seq = new AtomicLong(100L);

        when(importItemRepository.findByImportacaoIdAndXmlPath(eq(6L), any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1, String.class);
            return Optional.ofNullable(store.get(path));
        });
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(invocation -> {
            ImportItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                ReflectionTestUtils.setField(item, "id", seq.getAndIncrement());
            }
            store.put(item.getXmlPath(), item);
            return item;
        });

        byte[] zipBytes = buildZipOnlyXml();
        when(storageService.get("imports/6/abc.zip")).thenAnswer(invocation -> new ByteArrayInputStream(zipBytes));

        ExtractZipMessage message = new ExtractZipMessage(
                6L,
                "fiscal-raw",
                "imports/6/abc.zip",
                "abc"
        );

        service.process(message, "corr-6");
        service.process(message, "corr-6b");

        verify(importItemRepository, org.mockito.Mockito.times(2)).save(any(ImportItem.class));
    }

    @Test
    void process_zipInvalido_marcaFalha_semCriarItens() {
        ExtractZipService service = new ExtractZipService(
                importacaoRepository,
                importItemRepository,
                storageService,
                eventPublisher,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 3L);
        importacao.setStatus(ImportacaoStatus.RECEBIDO);

        when(importacaoRepository.findById(3L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InputStream badStream = new InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new ZipException("corrupt");
            }
        };
        when(storageService.get("imports/3/bad.zip")).thenReturn(badStream);

        ExtractZipMessage message = new ExtractZipMessage(
                3L,
                "fiscal-raw",
                "imports/3/bad.zip",
                "bad"
        );

        service.process(message, "corr-3");

        ArgumentCaptor<Importacao> importCaptor = ArgumentCaptor.forClass(Importacao.class);
        verify(importacaoRepository, atLeast(2)).save(importCaptor.capture());
        Importacao last = importCaptor.getAllValues().get(importCaptor.getAllValues().size() - 1);
        assertEquals(ImportacaoStatus.FALHA, last.getStatus());
        assertEquals("ZIP_INVALIDO", last.getErroCodigo());
        assertTrue(last.getErroMensagem() != null && !last.getErroMensagem().isBlank());

        verify(importItemRepository, never()).save(any(ImportItem.class));
        verify(eventPublisher, never()).publishEvent(any(ParseXmlRequestedEvent.class));
    }

    @Test
    void process_semXmls_marcaExtraido_semPublicar() throws Exception {
        ExtractZipService service = new ExtractZipService(
                importacaoRepository,
                importItemRepository,
                storageService,
                eventPublisher,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 4L);
        importacao.setStatus(ImportacaoStatus.RECEBIDO);

        when(importacaoRepository.findById(4L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] zipBytes = buildZipOnlyTxt();
        when(storageService.get("imports/4/none.zip")).thenReturn(new ByteArrayInputStream(zipBytes));

        ExtractZipMessage message = new ExtractZipMessage(
                4L,
                "fiscal-raw",
                "imports/4/none.zip",
                "none"
        );

        service.process(message, "corr-4");

        ArgumentCaptor<Importacao> importCaptor = ArgumentCaptor.forClass(Importacao.class);
        verify(importacaoRepository, atLeast(2)).save(importCaptor.capture());
        Importacao last = importCaptor.getAllValues().get(importCaptor.getAllValues().size() - 1);
        assertEquals(ImportacaoStatus.EXTRAIDO, last.getStatus());
        assertEquals(0, last.getTotalEncontrado());

        verify(eventPublisher, never()).publishEvent(any(ParseXmlRequestedEvent.class));
    }

    @Test
    void process_falhaMinio_lancaExcecao_semMarcarFalha() {
        ExtractZipService service = new ExtractZipService(
                importacaoRepository,
                importItemRepository,
                storageService,
                eventPublisher,
                new SimpleMeterRegistry()
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 5L);
        importacao.setStatus(ImportacaoStatus.RECEBIDO);

        when(importacaoRepository.findById(5L)).thenReturn(Optional.of(importacao));
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.get("imports/5/timeout.zip")).thenThrow(new RuntimeException("timeout"));

        ExtractZipMessage message = new ExtractZipMessage(
                5L,
                "fiscal-raw",
                "imports/5/timeout.zip",
                "timeout"
        );

        assertThrows(RuntimeException.class, () -> service.process(message, "corr-5"));

        ArgumentCaptor<Importacao> importCaptor = ArgumentCaptor.forClass(Importacao.class);
        verify(importacaoRepository, atLeast(1)).save(importCaptor.capture());
        List<Importacao> saved = importCaptor.getAllValues();
        boolean anyFalha = saved.stream().anyMatch(i -> i.getStatus() == ImportacaoStatus.FALHA);
        assertEquals(false, anyFalha);
    }

    private byte[] buildZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry xml1 = new ZipEntry("a.xml");
            zos.putNextEntry(xml1);
            zos.write("<xml/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry xml2 = new ZipEntry("b.xml");
            zos.putNextEntry(xml2);
            zos.write("<xml/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry txt = new ZipEntry("readme.txt");
            zos.putNextEntry(txt);
            zos.write("ignore".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] buildZipOnlyXml() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry xml1 = new ZipEntry("a.xml");
            zos.putNextEntry(xml1);
            zos.write("<xml/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry xml2 = new ZipEntry("b.xml");
            zos.putNextEntry(xml2);
            zos.write("<xml/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] buildZipOnlyTxt() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry txt = new ZipEntry("readme.txt");
            zos.putNextEntry(txt);
            zos.write("ignore".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
