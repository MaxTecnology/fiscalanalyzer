package br.com.techbr.fiscalanalyzer.xml.model;

import br.com.techbr.fiscalanalyzer.common.enums.ModelType;
import br.com.techbr.fiscalanalyzer.common.enums.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ParsedDocument(
        ModelType model,
        String accessKey,
        LocalDate issueDate,
        Instant issueDateTime,
        OperationType operationType,
        String emitCnpj,
        String destCnpj,
        BigDecimal totalProducts,
        BigDecimal totalAmount,
        BigDecimal totalIcms,
        BigDecimal totalPis,
        BigDecimal totalCofins,
        List<ParsedItem> items
) {}
