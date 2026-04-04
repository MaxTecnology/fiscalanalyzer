package br.com.techbr.fiscalanalyzer.documento.dto;

import java.math.BigDecimal;

public record DocumentCodeStatsResponse(
        String codigo,
        long totalItens,
        BigDecimal totalValor
) {}
