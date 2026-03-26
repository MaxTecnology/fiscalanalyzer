package br.com.techbr.fiscalanalyzer.xml.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedNfeDuplicate(
        String duplicateNumber,
        LocalDate dueDate,
        BigDecimal amount
) {}
