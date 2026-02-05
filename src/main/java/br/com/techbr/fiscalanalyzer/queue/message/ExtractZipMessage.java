package br.com.techbr.fiscalanalyzer.queue.message;

public record ExtractZipMessage(
        long importacaoId,
        String bucket,
        String objectKey,
        String sha256
) {}
