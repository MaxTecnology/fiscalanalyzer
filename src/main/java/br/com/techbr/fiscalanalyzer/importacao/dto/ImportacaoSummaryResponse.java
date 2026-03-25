package br.com.techbr.fiscalanalyzer.importacao.dto;

import java.time.Instant;

public record ImportacaoSummaryResponse(
        Long id,
        String status,
        String sourceType,
        long totalItems,
        long processedItems,
        long failedItems,
        Instant createdAt,
        Instant updatedAt
) {
}
