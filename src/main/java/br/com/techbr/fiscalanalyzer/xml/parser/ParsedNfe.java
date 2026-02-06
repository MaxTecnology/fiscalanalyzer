package br.com.techbr.fiscalanalyzer.xml.parser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ParsedNfe(
        short model,
        String accessKey,
        LocalDate issueDate,
        Instant issueDateTime,
        String operationType,
        String emitCnpj,
        String destCnpj,
        BigDecimal totalProducts,
        BigDecimal totalAmount,
        BigDecimal totalIcms,
        BigDecimal totalPis,
        BigDecimal totalCofins,
        List<ParsedNfeItem> items
) {}
