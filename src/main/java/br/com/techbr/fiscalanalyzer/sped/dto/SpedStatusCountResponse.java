package br.com.techbr.fiscalanalyzer.sped.dto;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;

public record SpedStatusCountResponse(
        SpedFiscalFileStatus status,
        long quantidade
) {
}

