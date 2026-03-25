package br.com.techbr.fiscalanalyzer.agent.dto;

public record AgentUploadUrlResponse(
        String uploadUrl,
        String objectKey,
        int expiresIn
) {}
