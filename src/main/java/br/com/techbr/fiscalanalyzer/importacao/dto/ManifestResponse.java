package br.com.techbr.fiscalanalyzer.importacao.dto;

public record ManifestResponse(
        Long importacaoId,
        String status,
        int totalItems
) {}
