package br.com.techbr.fiscalanalyzer.documento.dto;

import java.time.Instant;

public record FiscalDocumentEventResponse(
        String tipo,
        String codigoEvento,
        String descricao,
        Instant dataEvento,
        String protocolo,
        Integer status,
        String statusMotivo
) {}
