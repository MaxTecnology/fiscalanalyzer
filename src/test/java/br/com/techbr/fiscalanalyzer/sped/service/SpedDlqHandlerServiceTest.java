package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpedDlqHandlerServiceTest {

    @Test
    void markFileFalhaPermanente_atualiza_status_e_erro() {
        SpedFiscalFileRepository repository = mock(SpedFiscalFileRepository.class);
        SpedFiscalFile file = new SpedFiscalFile();
        file.setStatus(SpedFiscalFileStatus.PROCESSANDO);
        when(repository.findById(10L)).thenReturn(Optional.of(file));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpedDlqHandlerService service = new SpedDlqHandlerService(repository);
        service.markFileFalhaPermanente(10L, "retry exhausted");

        verify(repository).save(file);
        assertEquals(SpedFiscalFileStatus.FALHA, file.getStatus());
        assertEquals("FALHA_PERMANENTE", file.getErroCodigo());
        assertEquals("retry exhausted", file.getErroMensagem());
    }
}

