package br.com.techbr.fiscalanalyzer.documento.dto;

public record DocumentCoverageBackfillResponse(
        Long tenantId,
        Long empresaId,
        int requestedLimit,
        int processedItems,
        int updatedDocuments,
        int skippedItems,
        int failedItems
) {}
