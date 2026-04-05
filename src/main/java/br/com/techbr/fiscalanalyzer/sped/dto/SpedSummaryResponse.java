package br.com.techbr.fiscalanalyzer.sped.dto;

import java.time.LocalDate;
import java.util.List;

public record SpedSummaryResponse(
        Long tenantId,
        Long empresaId,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        long totalArquivos,
        List<SpedStatusCountResponse> status
) {
}

