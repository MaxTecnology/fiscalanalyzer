package br.com.techbr.fiscalanalyzer.importacao.dto;

public record ImportItemStatusCountResponse(
        String status,
        long count
) {}
