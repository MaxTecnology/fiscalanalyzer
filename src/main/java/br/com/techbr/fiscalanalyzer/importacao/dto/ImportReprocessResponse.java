package br.com.techbr.fiscalanalyzer.importacao.dto;

import java.util.List;

public record ImportReprocessResponse(
        Long importacaoId,
        boolean dryRun,
        String correlationId,
        int selectedItems,
        int requeuedItems,
        List<String> statuses,
        String erroCodigo
) {}
