package br.com.techbr.fiscalanalyzer.documento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentSummaryResponse(
        long totalDocumentos,
        long totalNFe,
        long totalNFCe,
        long totalCanceladas,
        BigDecimal valorTotalEntradas,
        BigDecimal valorTotalSaidas,
        BigDecimal icmsTotalSaidas,
        LocalDate periodoInicio,
        LocalDate periodoFim
) {}
