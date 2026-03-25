package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestEntryRequest;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestRequest;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.queue.producer.ExtractZipProducer;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacaoServiceTest {

    @Mock
    private ImportacaoRepository importacaoRepository;

    @Mock
    private ImportItemRepository importItemRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ExtractZipProducer extractZipProducer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TenantEmpresaValidationService tenantEmpresaValidationService;

    private ImportacaoService service;

    @BeforeEach
    void setUp() {
        service = new ImportacaoService(
                importacaoRepository,
                importItemRepository,
                storageService,
                extractZipProducer,
                eventPublisher,
                tenantEmpresaValidationService,
                DataSize.ofMegabytes(5),
                "fiscal-raw"
        );
    }

    @Test
    void criarImportacao_salvaEPublica() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teste.zip",
                "application/zip",
                "abc".getBytes(StandardCharsets.UTF_8)
        );

        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> {
            Importacao imp = invocation.getArgument(0);
            if (imp.getId() == null) {
                ReflectionTestUtils.setField(imp, "id", 10L);
            }
            return imp;
        });

        Importacao result = service.criarImportacao(1L, 2L, file);

        String expectedHash = sha256Hex("abc");
        String expectedKey = "imports/10/" + expectedHash + ".zip";

        verify(storageService).put(eq(expectedKey), any(java.io.InputStream.class), eq((long) file.getSize()), eq("application/zip"));

        ArgumentCaptor<ExtractZipMessage> msgCaptor = ArgumentCaptor.forClass(ExtractZipMessage.class);
        verify(extractZipProducer).send(msgCaptor.capture());
        ExtractZipMessage message = msgCaptor.getValue();
        assertEquals(10L, message.importacaoId());
        assertEquals("fiscal-raw", message.bucket());
        assertEquals(expectedKey, message.objectKey());
        assertEquals(expectedHash, message.sha256());

        assertEquals(expectedHash, result.getArquivoHash());
    }

    @Test
    void criarImportacao_rejeitaExtensaoInvalida() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teste.txt",
                "text/plain",
                "abc".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(ValidationException.class, () -> service.criarImportacao(1L, 2L, file));
    }

    @Test
    void criarImportacao_retorna422_quandoTenantEmpresaNaoExiste() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teste.zip",
                "application/zip",
                "abc".getBytes(StandardCharsets.UTF_8)
        );
        doThrow(new UnprocessableEntityException("empresaId nao encontrado"))
                .when(tenantEmpresaValidationService).validateAtivo(1L, 2L);

        assertThrows(UnprocessableEntityException.class, () -> service.criarImportacao(1L, 2L, file));
        verify(tenantEmpresaValidationService).validateAtivo(1L, 2L);
        verifyNoInteractions(storageService, extractZipProducer);
        verify(importacaoRepository, never()).save(any(Importacao.class));
    }

    @Test
    void criarImportacaoPorManifesto_criaItensEPublicaEventos() {
        ManifestEntryRequest entry1 = new ManifestEntryRequest(
                "99/99/a.xml",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                100L,
                "a.xml"
        );
        ManifestEntryRequest entry2 = new ManifestEntryRequest(
                "99/99/b.xml",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                200L,
                "b.xml"
        );
        ManifestRequest request = new ManifestRequest(99L, 99L, java.util.List.of(entry1, entry2));

        when(storageService.exists(entry1.objectKey())).thenReturn(true);
        when(storageService.exists(entry2.objectKey())).thenReturn(true);
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> {
            Importacao imp = invocation.getArgument(0);
            if (imp.getId() == null) {
                ReflectionTestUtils.setField(imp, "id", 42L);
            }
            return imp;
        });
        when(importItemRepository.findByImportacaoIdAndXmlPath(eq(42L), any())).thenReturn(java.util.Optional.empty());
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(invocation -> {
            ImportItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                long next = "99/99/a.xml".equals(item.getXmlPath()) ? 100L : 101L;
                ReflectionTestUtils.setField(item, "id", next);
            }
            return item;
        });

        Importacao result = service.criarImportacaoPorManifesto(request, 99L, 99L);

        assertEquals(42L, result.getId());
        assertEquals(ImportacaoSourceType.MANIFEST, result.getSourceType());
        assertEquals(2, result.getTotalEncontrado());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        java.util.List<ParseXmlRequestedEvent> events = eventCaptor.getAllValues().stream()
                .filter(ParseXmlRequestedEvent.class::isInstance)
                .map(ParseXmlRequestedEvent.class::cast)
                .toList();
        assertEquals(2, events.size());
        assertEquals(2, events.stream().filter(e -> e.importacaoId() == 42L).count());
        assertEquals(2, events.stream().filter(e -> e.zipEntryName() == null).count());
    }

    @Test
    void criarImportacaoPorManifesto_retorna422_quandoObjetoNaoExiste() {
        ManifestRequest request = new ManifestRequest(
                99L,
                99L,
                java.util.List.of(new ManifestEntryRequest(
                        "99/99/a.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        100L,
                        "a.xml"
                ))
        );

        when(storageService.exists("99/99/a.xml")).thenReturn(false);

        assertThrows(UnprocessableEntityException.class, () -> service.criarImportacaoPorManifesto(request, 99L, 99L));
    }

    @Test
    void criarImportacaoPorManifesto_rejeitaTenantEmpresaDiferentesDaApiKey() {
        ManifestRequest request = new ManifestRequest(
                99L,
                99L,
                java.util.List.of(new ManifestEntryRequest(
                        "99/99/a.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        100L,
                        "a.xml"
                ))
        );

        assertThrows(br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException.class,
                () -> service.criarImportacaoPorManifesto(request, 1L, 2L));
    }

    @Test
    void criarImportacaoPorManifesto_retorna403_quandoTenantOuEmpresaInativos() {
        ManifestRequest request = new ManifestRequest(
                99L,
                99L,
                java.util.List.of(new ManifestEntryRequest(
                        "99/99/a.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        100L,
                        "a.xml"
                ))
        );
        doThrow(new br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException("empresa inativa"))
                .when(tenantEmpresaValidationService).validateAtivo(99L, 99L);

        assertThrows(br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException.class,
                () -> service.criarImportacaoPorManifesto(request, 99L, 99L));
        verify(tenantEmpresaValidationService).validateAtivo(99L, 99L);
        verifyNoInteractions(storageService);
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
