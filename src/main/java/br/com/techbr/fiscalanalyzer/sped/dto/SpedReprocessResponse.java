package br.com.techbr.fiscalanalyzer.sped.dto;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;

public record SpedReprocessResponse(
        Long id,
        SpedFiscalFileStatus status
) {
}

