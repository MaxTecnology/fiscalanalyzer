package br.com.techbr.fiscalanalyzer.sped.dto;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileSourceType;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SpedFiscalFileSummaryResponse(
        Long id,
        SpedFiscalFileStatus status,
        SpedFiscalFileSourceType sourceType,
        String originalFileName,
        String hashSha256,
        String layoutVersion,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        String erroCodigo,
        String erroMensagem,
        Instant createdAt,
        Instant updatedAt
) {
}

