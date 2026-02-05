package br.com.techbr.fiscalanalyzer.queue.message;

public record ParseXmlMessage(
        long importacaoId,
        long importItemId,
        String bucket,
        String objectKeyZip,
        String zipEntryName,
        String sha256
) {}
