package br.com.techbr.fiscalanalyzer.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentUploadUrlRequest(
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sha256,
        @Size(max = 255) String originalFileName,
        @Min(0) Long sizeBytes
) {}
