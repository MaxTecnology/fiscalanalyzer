package br.com.techbr.fiscalanalyzer.documento.dto;

public record DocumentCoverageSummaryResponse(
        Long tenantId,
        Long empresaId,
        long totalDocuments,
        long missingIdeCoverage,
        long missingEmitCoverage,
        long missingDestCoverage,
        long missingTotalsCoverage,
        long missingProtocolCoverage,
        long documentsWithoutPayments,
        long documentsWithoutAdditionalInfo
) {}
