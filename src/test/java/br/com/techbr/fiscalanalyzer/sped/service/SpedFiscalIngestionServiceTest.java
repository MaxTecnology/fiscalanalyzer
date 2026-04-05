package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedUploadResponse;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpedFiscalIngestionServiceTest {

    private SpedFiscalFileRepository spedFiscalFileRepository;
    private StorageService storageService;
    private TenantEmpresaValidationService tenantEmpresaValidationService;
    private ApplicationEventPublisher eventPublisher;
    private SpedFiscalIngestionService service;

    @BeforeEach
    void setUp() {
        spedFiscalFileRepository = mock(SpedFiscalFileRepository.class);
        storageService = mock(StorageService.class);
        tenantEmpresaValidationService = mock(TenantEmpresaValidationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new SpedFiscalIngestionService(
                spedFiscalFileRepository,
                storageService,
                tenantEmpresaValidationService,
                eventPublisher,
                DataSize.ofMegabytes(300),
                DataSize.ofMegabytes(10),
                "fiscal-analyzer"
        );
    }

    @Test
    void upload_txt_cria_registro_e_enfileira() {
        MockMultipartFile file = new MockMultipartFile("file", "sped.txt", "text/plain", "|0000|...".getBytes());
        doNothing().when(tenantEmpresaValidationService).validateAtivo(1L, 2L);
        when(spedFiscalFileRepository.findByTenantIdAndEmpresaIdAndHashSha256(eq(1L), eq(2L), anyString()))
                .thenReturn(Optional.empty());
        when(spedFiscalFileRepository.save(any())).thenAnswer(invocation -> {
            SpedFiscalFile entity = invocation.getArgument(0);
            setId(entity, 101L);
            return entity;
        });

        SpedUploadResponse response = service.upload(1L, 2L, file);

        assertEquals("TXT", response.inputType());
        assertEquals(1, response.totalCandidates());
        assertEquals(1, response.acceptedFiles());
        assertEquals(0, response.duplicatedFiles());
        assertEquals(1, response.queuedFiles());
        verify(storageService, times(1)).put(anyString(), any(), anyLong(), eq("text/plain"));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void upload_zip_processa_multiplos_txt() throws IOException {
        byte[] zipBytes = buildZip(
                "arquivo-a.txt", "|0000|A|",
                "arquivo-b.txt", "|0000|B|",
                "nao-processar.xml", "<x/>"
        );
        MockMultipartFile file = new MockMultipartFile("file", "sped-lote.zip", "application/zip", zipBytes);

        doNothing().when(tenantEmpresaValidationService).validateAtivo(1L, 2L);
        when(spedFiscalFileRepository.findByTenantIdAndEmpresaIdAndHashSha256(eq(1L), eq(2L), anyString()))
                .thenReturn(Optional.empty(), Optional.of(new SpedFiscalFile()));
        when(spedFiscalFileRepository.save(any())).thenAnswer(invocation -> {
            SpedFiscalFile entity = invocation.getArgument(0);
            setId(entity, 202L);
            return entity;
        });

        SpedUploadResponse response = service.upload(1L, 2L, file);

        assertEquals("ZIP", response.inputType());
        assertEquals(2, response.totalCandidates());
        assertEquals(1, response.acceptedFiles());
        assertEquals(1, response.duplicatedFiles());
        assertEquals(1, response.queuedFiles());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService, times(1)).put(keyCaptor.capture(), any(), eq((long) zipBytes.length), eq("application/zip"));
        assertTrue(keyCaptor.getValue().endsWith(".zip"));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void upload_zip_sem_txt_retorna_erro() throws IOException {
        byte[] zipBytes = buildZip("evento.xml", "<x/>", "lote.csv", "x;y;z");
        MockMultipartFile file = new MockMultipartFile("file", "sped.zip", "application/zip", zipBytes);
        doNothing().when(tenantEmpresaValidationService).validateAtivo(1L, 2L);

        assertThrows(ValidationException.class, () -> service.upload(1L, 2L, file));
    }

    private byte[] buildZip(String name1, String content1, String name2, String content2) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, name1, content1);
            addEntry(zos, name2, content2);
            return baos.toByteArray();
        }
    }

    private byte[] buildZip(String name1, String content1, String name2, String content2, String name3, String content3)
            throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, name1, content1);
            addEntry(zos, name2, content2);
            addEntry(zos, name3, content3);
            return baos.toByteArray();
        }
    }

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes());
        zos.closeEntry();
    }

    private void setId(SpedFiscalFile entity, Long id) {
        try {
            Field field = SpedFiscalFile.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
