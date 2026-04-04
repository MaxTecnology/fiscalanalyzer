package br.com.techbr.fiscalanalyzer.documento.dto;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record DocumentExportRequest(
        @NotNull @Min(1) Long tenantId,
        @NotNull @Min(1) Long empresaId,
        Short model,
        String operationType,
        FiscalDocumentStatus statusDocumento,
        String emitCnpj,
        String destCnpj,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDateTo,
        Long importacaoId,
        DocumentExportFormat format
) {}
