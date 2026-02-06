package br.com.techbr.fiscalanalyzer.xml.parser;

import java.math.BigDecimal;

public record ParsedNfeItem(
        Integer itemNumber,
        String productCode,
        String productDescription,
        String ncm,
        String cfop,
        String cstIcms,
        String csosn,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        BigDecimal icmsBase,
        BigDecimal icmsRate,
        BigDecimal icmsValue,
        BigDecimal pisBase,
        BigDecimal pisRate,
        BigDecimal pisValue,
        BigDecimal cofinsBase,
        BigDecimal cofinsRate,
        BigDecimal cofinsValue
) {}
