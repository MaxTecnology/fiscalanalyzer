package br.com.techbr.fiscalanalyzer.sped.dto;

import java.math.BigDecimal;

public record SpedCodeStatsResponse(
        String codigo,
        Long totalItens,
        BigDecimal totalValor
) {
}

