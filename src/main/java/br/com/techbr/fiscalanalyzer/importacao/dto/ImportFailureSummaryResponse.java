package br.com.techbr.fiscalanalyzer.importacao.dto;

import java.time.Instant;

public record ImportFailureSummaryResponse(
        String erroCodigo,
        String erroMensagem,
        long totalItems,
        Long sampleItemId,
        String sampleXmlPath,
        Instant lastOccurrenceAt
) {}
