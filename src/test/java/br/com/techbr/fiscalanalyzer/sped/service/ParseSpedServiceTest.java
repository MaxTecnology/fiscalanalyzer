package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.queue.message.ParseSpedMessage;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileSourceType;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC100Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC170Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC190Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE110Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE111Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalParticipantRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParseSpedServiceTest {

    private SpedFiscalFileRepository spedFiscalFileRepository;
    private SpedFiscalParticipantRepository participantRepository;
    private SpedFiscalC100Repository spedFiscalC100Repository;
    private SpedFiscalC170Repository spedFiscalC170Repository;
    private SpedFiscalC190Repository spedFiscalC190Repository;
    private SpedFiscalE110Repository spedFiscalE110Repository;
    private SpedFiscalE111Repository spedFiscalE111Repository;
    private StorageService storageService;
    private ParseSpedService parseSpedService;

    @BeforeEach
    void setUp() {
        spedFiscalFileRepository = mock(SpedFiscalFileRepository.class);
        participantRepository = mock(SpedFiscalParticipantRepository.class);
        spedFiscalC100Repository = mock(SpedFiscalC100Repository.class);
        spedFiscalC170Repository = mock(SpedFiscalC170Repository.class);
        spedFiscalC190Repository = mock(SpedFiscalC190Repository.class);
        spedFiscalE110Repository = mock(SpedFiscalE110Repository.class);
        spedFiscalE111Repository = mock(SpedFiscalE111Repository.class);
        storageService = mock(StorageService.class);
        parseSpedService = new ParseSpedService(
                spedFiscalFileRepository,
                participantRepository,
                spedFiscalC100Repository,
                spedFiscalC170Repository,
                spedFiscalC190Repository,
                spedFiscalE110Repository,
                spedFiscalE111Repository,
                storageService,
                new SimpleMeterRegistry()
        );
        when(spedFiscalFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spedFiscalC100Repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spedFiscalC170Repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spedFiscalC190Repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spedFiscalE110Repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spedFiscalE111Repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void process_txt_valido_marca_processado() {
        SpedFiscalFile file = buildFile(1L, SpedFiscalFileSourceType.TXT, "sped/1/2/a.txt", null);
        when(spedFiscalFileRepository.findById(1L)).thenReturn(Optional.of(file));
        byte[] txtBytes = validSpedTxt().getBytes(StandardCharsets.UTF_8);
        when(storageService.get("sped/1/2/a.txt")).thenAnswer(invocation -> new ByteArrayInputStream(txtBytes));

        parseSpedService.process(new ParseSpedMessage(1L, "bucket", "sped/1/2/a.txt", null, "hash", "corr"), "corr");

        ArgumentCaptor<SpedFiscalFile> captor = ArgumentCaptor.forClass(SpedFiscalFile.class);
        verify(spedFiscalFileRepository, atLeastOnce()).save(captor.capture());
        SpedFiscalFile lastSaved = captor.getValue();
        assertEquals(SpedFiscalFileStatus.PROCESSADO, lastSaved.getStatus());
        assertEquals("020", lastSaved.getLayoutVersion());
        assertEquals(9, lastSaved.getLineCountDeclared());
        assertEquals(9, lastSaved.getLineCountProcessed());
        verify(spedFiscalC100Repository).deleteByFileId(1L);
        verify(participantRepository).deleteByFileId(1L);
        verify(spedFiscalC170Repository).deleteByC100FileId(1L);
        verify(spedFiscalC190Repository).deleteByC100FileId(1L);
        verify(spedFiscalE110Repository).deleteByFileId(1L);
        verify(spedFiscalE111Repository).deleteByE110FileId(1L);
        verify(participantRepository, atLeastOnce()).save(any());
        verify(spedFiscalC100Repository, atLeastOnce()).save(any());
        verify(spedFiscalC170Repository, atLeastOnce()).save(any());
        verify(spedFiscalC190Repository, atLeastOnce()).save(any());
        verify(spedFiscalE110Repository, atLeastOnce()).save(any());
        verify(spedFiscalE111Repository, atLeastOnce()).save(any());
    }

    @Test
    void process_zip_entry_valido_marca_processado() throws Exception {
        SpedFiscalFile file = buildFile(2L, SpedFiscalFileSourceType.ZIP_ENTRY, "sped/1/2/lote.zip", "entrada/sped.txt");
        when(spedFiscalFileRepository.findById(2L)).thenReturn(Optional.of(file));
        byte[] zipBytes = buildZip("entrada/sped.txt", validSpedTxt());
        when(storageService.get("sped/1/2/lote.zip")).thenAnswer(invocation -> new ByteArrayInputStream(zipBytes));

        parseSpedService.process(new ParseSpedMessage(2L, "bucket", "sped/1/2/lote.zip", "entrada/sped.txt", "hash", "corr"), "corr");

        verify(spedFiscalFileRepository, atLeastOnce()).save(any());
        assertEquals(SpedFiscalFileStatus.PROCESSADO, file.getStatus());
        assertEquals("020", file.getLayoutVersion());
    }

    @Test
    void process_conteudo_invalido_marca_falha_sem_lancar() {
        SpedFiscalFile file = buildFile(3L, SpedFiscalFileSourceType.TXT, "sped/1/2/bad.txt", null);
        when(spedFiscalFileRepository.findById(3L)).thenReturn(Optional.of(file));
        when(storageService.get("sped/1/2/bad.txt")).thenReturn(new ByteArrayInputStream("|9999|1|\n".getBytes(StandardCharsets.UTF_8)));

        parseSpedService.process(new ParseSpedMessage(3L, "bucket", "sped/1/2/bad.txt", null, "hash", "corr"), "corr");

        assertEquals(SpedFiscalFileStatus.FALHA, file.getStatus());
        assertEquals("FALHA_PARSE", file.getErroCodigo());
    }

    @Test
    void process_erro_infra_relanca_para_retry() {
        SpedFiscalFile file = buildFile(4L, SpedFiscalFileSourceType.TXT, "sped/1/2/fail.txt", null);
        when(spedFiscalFileRepository.findById(4L)).thenReturn(Optional.of(file));
        when(storageService.get(anyString())).thenThrow(new RuntimeException("storage down"));

        assertThrows(InfraException.class, () ->
                parseSpedService.process(new ParseSpedMessage(4L, "bucket", "sped/1/2/fail.txt", null, "hash", "corr"), "corr"));
    }

    @Test
    void process_e111_sem_e110_marca_falha_parse() {
        SpedFiscalFile file = buildFile(5L, SpedFiscalFileSourceType.TXT, "sped/1/2/e111-sem-e110.txt", null);
        when(spedFiscalFileRepository.findById(5L)).thenReturn(Optional.of(file));
        when(storageService.get("sped/1/2/e111-sem-e110.txt"))
                .thenReturn(new ByteArrayInputStream(invalidSpedE111WithoutE110().getBytes(StandardCharsets.UTF_8)));

        parseSpedService.process(
                new ParseSpedMessage(5L, "bucket", "sped/1/2/e111-sem-e110.txt", null, "hash", "corr"),
                "corr"
        );

        assertEquals(SpedFiscalFileStatus.FALHA, file.getStatus());
        assertEquals("FALHA_PARSE", file.getErroCodigo());
        assertEquals("sped_e111_sem_e110_linha_2", file.getErroMensagem());
    }

    private SpedFiscalFile buildFile(Long id, SpedFiscalFileSourceType sourceType, String objectKey, String zipEntry) {
        SpedFiscalFile file = new SpedFiscalFile();
        setId(file, id);
        file.setTenantId(1L);
        file.setEmpresaId(2L);
        file.setSourceType(sourceType);
        file.setStatus(SpedFiscalFileStatus.PENDENTE_PARSE);
        file.setOriginalFileName("arquivo.txt");
        file.setStorageObjectKey(objectKey);
        file.setZipEntryName(zipEntry);
        file.setHashSha256("hash");
        file.setContentType("text/plain");
        return file;
    }

    private String validSpedTxt() {
        return """
                |0000|020|0|01012026|31012026|EMPRESA|12345678000199|SP|110042490114|
                |0150|PART001|PARTICIPANTE TESTE|1058|12345678000199||123456789|3550308||||||
                |C100|1|0|PART001|55|00|1|123|26250111111111111111550010000000011000000010|02012026|02012026|100,00|0|0,00|0,00|100,00|0|0,00|0,00|0,00|100,00|18,00|0,00|0,00|0,00|0,00|0,00|0,00|
                |C170|1|PROD-001|ITEM 1|10,000|UN|100,00|0,00|0|060|5102||100,00|18,00|18,00|0,00|0,00|0,00|0|50||0,00|0,00|0,00|01|0,00|0,00|0,00|01|0,00|0,00|0,00||
                |C190|060|5102|18,00|100,00|100,00|18,00|0,00|0,00|0,00|0,00||
                |E110|100,00|0,00|0,00|0,00|80,00|0,00|0,00|0,00|0,00|20,00|0,00|20,00|0,00|0,00|
                |E111|SP000001|AJUSTE TESTE|5,00|
                |0001|0|
                |9999|9|
                """;
    }

    private String invalidSpedE111WithoutE110() {
        return """
                |0000|020|0|01012026|31012026|EMPRESA|12345678000199|SP|110042490114|
                |E111|SP000001|AJUSTE TESTE|5,00|
                |9999|3|
                """;
    }

    private byte[] buildZip(String entryName, String content) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        }
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
