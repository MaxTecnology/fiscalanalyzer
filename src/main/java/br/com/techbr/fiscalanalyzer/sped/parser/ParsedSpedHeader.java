package br.com.techbr.fiscalanalyzer.sped.parser;

import java.time.LocalDate;

public record ParsedSpedHeader(
        String layoutVersion,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        Integer lineCountDeclared,
        int lineCountProcessed
) {
}

