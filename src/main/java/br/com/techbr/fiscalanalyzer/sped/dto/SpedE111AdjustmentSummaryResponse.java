package br.com.techbr.fiscalanalyzer.sped.dto;

import java.math.BigDecimal;

public record SpedE111AdjustmentSummaryResponse(
        String codigoAjuste,
        Long totalRegistros,
        BigDecimal totalValor
) {
}

