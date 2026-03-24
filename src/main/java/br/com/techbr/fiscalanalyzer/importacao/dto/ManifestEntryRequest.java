package br.com.techbr.fiscalanalyzer.importacao.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ManifestEntryRequest(
        @NotBlank String objectKey,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sha256,
        @Min(0) Long sizeBytes,
        String originalFileName
) {}
