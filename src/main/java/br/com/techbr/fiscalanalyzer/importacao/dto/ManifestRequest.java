package br.com.techbr.fiscalanalyzer.importacao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ManifestRequest(
        @NotNull @Min(1) Long tenantId,
        @NotNull @Min(1) Long empresaId,
        @NotEmpty List<@Valid ManifestEntryRequest> entries
) {}
