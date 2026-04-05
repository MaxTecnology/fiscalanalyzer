package br.com.techbr.fiscalanalyzer.sped.event;

public record ParseSpedRequestedEvent(
        Long spedFileId,
        String bucket,
        String objectKey,
        String zipEntryName,
        String sha256,
        String correlationId
) {
}

