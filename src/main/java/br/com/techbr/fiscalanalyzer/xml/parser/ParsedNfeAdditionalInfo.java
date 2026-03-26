package br.com.techbr.fiscalanalyzer.xml.parser;

public record ParsedNfeAdditionalInfo(
        String infoType,
        String fieldName,
        String textValue
) {}
