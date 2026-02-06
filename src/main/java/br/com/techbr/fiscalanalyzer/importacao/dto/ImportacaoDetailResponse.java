package br.com.techbr.fiscalanalyzer.importacao.dto;

import java.time.Instant;
import java.util.List;

public record ImportacaoDetailResponse(
        Long id,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String arquivoNome,
        Long arquivoTamanho,
        String arquivoContentType,
        String sha256,
        String erroCodigo,
        String erroMensagem,
        long totalItems,
        List<ImportItemStatusCountResponse> statusCounts
) {}
