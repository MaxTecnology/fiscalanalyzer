package br.com.techbr.fiscalanalyzer.sped.dto;

import br.com.techbr.fiscalanalyzer.sped.model.SpedReconciliationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpedReconciliationDivergenceResponse(
        Long spedFileId,
        Long spedC100Id,
        Integer linhaOrigem,
        String accessKey,
        LocalDate issueDate,
        String model,
        String numeroDocumento,
        String serie,
        BigDecimal totalSped,
        BigDecimal totalXml,
        BigDecimal totalIcmsSped,
        BigDecimal totalIcmsXml,
        BigDecimal totalPisSped,
        BigDecimal totalPisXml,
        BigDecimal totalCofinsSped,
        BigDecimal totalCofinsXml,
        Long totalItemsSped,
        Long totalItemsXml,
        String divergenceReason,
        SpedReconciliationStatus status
) {
}

