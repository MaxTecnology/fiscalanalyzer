package br.com.techbr.fiscalanalyzer.sped.dto;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileSourceType;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SpedFiscalFileDetailResponse(
        Long id,
        Long tenantId,
        Long empresaId,
        SpedFiscalFileStatus status,
        SpedFiscalFileSourceType sourceType,
        String originalFileName,
        String storageObjectKey,
        String zipEntryName,
        String hashSha256,
        Long contentSize,
        String contentType,
        String layoutVersion,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        Integer lineCountDeclared,
        Integer lineCountProcessed,
        String erroCodigo,
        String erroMensagem,
        Instant createdAt,
        Instant updatedAt
) {
}

