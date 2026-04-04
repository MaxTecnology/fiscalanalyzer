package br.com.techbr.fiscalanalyzer.documento.dto;

import java.math.BigDecimal;

public record FiscalDocumentItemDetailResponse(
        Integer nItem,
        String codigoProduto,
        String descricao,
        String ncm,
        String cfop,
        String unidade,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        BigDecimal icmsAliq,
        BigDecimal icmsValor,
        BigDecimal pisAliq,
        BigDecimal pisValor,
        BigDecimal cofinsAliq,
        BigDecimal cofinsValor
) {}
