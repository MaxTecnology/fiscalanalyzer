package br.com.techbr.fiscalanalyzer.documento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentListItemResponse(
        String accessKey,
        short model,
        String nfeSerie,
        Integer nfeNumero,
        LocalDate issueDate,
        String operationType,
        String statusDocumento,
        String emitCnpj,
        String emitRazaoSocial,
        String destCnpj,
        String destRazaoSocial,
        BigDecimal totalAmount,
        Long importacaoId
) {}
