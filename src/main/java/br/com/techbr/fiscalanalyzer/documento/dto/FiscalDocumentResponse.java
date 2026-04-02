package br.com.techbr.fiscalanalyzer.documento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FiscalDocumentResponse(
        short model,
        String accessKey,
        LocalDate issueDate,
        String operationType,
        String emitCnpj,
        String destCnpj,
        BigDecimal totalAmount,
        Long importacaoId,
        String xmlPath,
        String xmlHash,
        String statusDocumento,
        Instant cancelledAt,
        String cancelProtocol,
        String cancelReason
) {}
