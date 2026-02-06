package br.com.techbr.fiscalanalyzer.importacao.event;

public record ParseXmlRequestedEvent(
        long importacaoId,
        long importItemId,
        String bucket,
        String objectKeyZip,
        String zipEntryName,
        String sha256,
        String correlationId
) {}
