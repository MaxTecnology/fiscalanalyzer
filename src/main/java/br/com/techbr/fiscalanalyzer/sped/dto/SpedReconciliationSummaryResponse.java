package br.com.techbr.fiscalanalyzer.sped.dto;

import java.time.LocalDate;
import java.util.List;

public record SpedReconciliationSummaryResponse(
        Long tenantId,
        Long empresaId,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        Long totalArquivosProcessados,
        Long totalDocumentosSped,
        Long totalOk,
        Long totalFaltanteXml,
        Long totalValorDivergente,
        Long totalItemDivergente,
        Long totalImpostoDivergente,
        Long totalSemChave,
        List<SpedCodeStatsResponse> cfopSpedTop,
        List<SpedCodeStatsResponse> cstSpedTop,
        List<SpedCodeStatsResponse> cfopXmlTop,
        List<SpedCodeStatsResponse> ncmXmlTop,
        SpedApuracaoSummaryResponse apuracao
) {
}

