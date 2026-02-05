package br.com.techbr.fiscalanalyzer.common.exception;

public record ErrorResponse(
        String code,
        String message
) {}
