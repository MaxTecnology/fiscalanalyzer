package br.com.techbr.fiscalanalyzer.importacao.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ImportItemResponse(
        Long id,
        String status,
        String xmlPath,
        Long xmlSize,
        String xmlHash,
        String accessKey,
        Short model,
        LocalDate issueDate,
        String erroCodigo,
        String erroMensagem,
        Instant createdAt,
        Instant updatedAt
) {}
