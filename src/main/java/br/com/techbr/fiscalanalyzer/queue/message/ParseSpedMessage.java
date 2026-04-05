package br.com.techbr.fiscalanalyzer.queue.message;

public record ParseSpedMessage(
        Long spedFileId,
        String bucket,
        String objectKey,
        String zipEntryName,
        String sha256,
        String correlationId
) {
}

