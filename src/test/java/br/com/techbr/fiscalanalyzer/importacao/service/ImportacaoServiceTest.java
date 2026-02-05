package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.queue.producer.ExtractZipProducer;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacaoServiceTest {

    @Mock
    private ImportacaoRepository importacaoRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ExtractZipProducer extractZipProducer;

    private ImportacaoService service;

    @BeforeEach
    void setUp() {
        service = new ImportacaoService(
                importacaoRepository,
                storageService,
                extractZipProducer,
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

    private String sha256Hex(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
