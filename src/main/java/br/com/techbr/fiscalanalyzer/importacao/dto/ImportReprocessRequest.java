package br.com.techbr.fiscalanalyzer.importacao.dto;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;

import java.util.List;

public record ImportReprocessRequest(
        List<ImportItemStatus> statuses,
        String erroCodigo,
        Integer limit,
        Boolean dryRun
) {}
