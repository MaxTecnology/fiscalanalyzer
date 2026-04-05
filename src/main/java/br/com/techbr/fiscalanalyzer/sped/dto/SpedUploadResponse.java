package br.com.techbr.fiscalanalyzer.sped.dto;

public record SpedUploadResponse(
        String inputType,
        int totalCandidates,
        int acceptedFiles,
        int duplicatedFiles,
        int queuedFiles
) {
}

