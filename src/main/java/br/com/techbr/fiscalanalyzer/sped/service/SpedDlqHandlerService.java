package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpedDlqHandlerService {

    private static final Logger log = LoggerFactory.getLogger(SpedDlqHandlerService.class);

    private final SpedFiscalFileRepository spedFiscalFileRepository;

    public SpedDlqHandlerService(SpedFiscalFileRepository spedFiscalFileRepository) {
        this.spedFiscalFileRepository = spedFiscalFileRepository;
    }

    @Transactional
    public void markFileFalhaPermanente(Long spedFileId, String cause) {
        SpedFiscalFile file = spedFiscalFileRepository.findById(spedFileId).orElse(null);
        if (file == null) {
            log.warn("dlq.sped.file_nao_encontrado spedFileId={}", spedFileId);
            return;
        }

        file.setStatus(SpedFiscalFileStatus.FALHA);
        file.setErroCodigo("FALHA_PERMANENTE");
        file.setErroMensagem(trim(cause, 500));
        spedFiscalFileRepository.save(file);

        log.error("dlq.sped.file_falha_permanente spedFileId={} causa={}", spedFileId, cause);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}

