package br.com.techbr.fiscalanalyzer.sped.parser;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class SpedFiscalHeaderParser {

    private static final DateTimeFormatter SPED_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");

    public ParsedSpedHeader parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new ValidationException("sped_arquivo_vazio");
        }

        String layoutVersion = null;
        LocalDate periodoInicio = null;
        LocalDate periodoFim = null;
        Integer lineCountDeclared = null;
        int lineCountProcessed = 0;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = normalizeLine(line);
                if (normalized.isBlank()) {
                    continue;
                }

                lineCountProcessed++;
                String[] fields = normalized.split("\\|", -1);
                if (fields.length < 2) {
                    continue;
                }

                String registro = fields[1];
                if ("0000".equals(registro)) {
                    layoutVersion = field(fields, 2);
                    periodoInicio = parseDate(field(fields, 4), "sped_periodo_inicio_invalido");
                    periodoFim = parseDate(field(fields, 5), "sped_periodo_fim_invalido");
                } else if ("9999".equals(registro)) {
                    lineCountDeclared = parseInteger(field(fields, 2), "sped_qtd_lin_invalida");
                }
            }
        } catch (IOException e) {
            throw new InfraException("Falha ao ler arquivo SPED", e);
        }

        if (lineCountProcessed == 0) {
            throw new ValidationException("sped_arquivo_vazio");
        }
        if (!StringUtils.hasText(layoutVersion) || periodoInicio == null || periodoFim == null) {
            throw new ValidationException("sped_registro_0000_obrigatorio_ausente");
        }
        if (lineCountDeclared == null) {
            throw new ValidationException("sped_registro_9999_obrigatorio_ausente");
        }
        if (lineCountDeclared != lineCountProcessed) {
            throw new ValidationException("sped_qtd_lin_divergente");
        }

        return new ParsedSpedHeader(layoutVersion, periodoInicio, periodoFim, lineCountDeclared, lineCountProcessed);
    }

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String normalized = line.strip();
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String field(String[] fields, int index) {
        if (index < 0 || index >= fields.length) {
            return "";
        }
        return fields[index] != null ? fields[index].trim() : "";
    }

    private LocalDate parseDate(String value, String validationCode) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, SPED_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(validationCode);
        }
    }

    private Integer parseInteger(String value, String validationCode) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ValidationException(validationCode);
        }
    }
}

