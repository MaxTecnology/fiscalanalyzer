package br.com.techbr.fiscalanalyzer.sped.dto;

import java.math.BigDecimal;
import java.util.List;

public record SpedApuracaoSummaryResponse(
        Long totalRegistrosE110,
        BigDecimal totalDebitos,
        BigDecimal totalCreditos,
        BigDecimal totalIcmsRecolher,
        BigDecimal totalAjustesE111,
        List<SpedE111AdjustmentSummaryResponse> topAjustes
) {
}

