package br.com.techbr.fiscalanalyzer.sped.parser;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpedFiscalHeaderParserTest {

    private final SpedFiscalHeaderParser parser = new SpedFiscalHeaderParser();

    @Test
    void parse_valido_retorna_header() {
        String content = """
                |0000|020|0|01012026|31012026|EMPRESA|12345678000199|SP|110042490114|
                |0001|0|
                |9999|3|
                """;

        ParsedSpedHeader parsed = parser.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertEquals("020", parsed.layoutVersion());
        assertEquals(LocalDate.of(2026, 1, 1), parsed.periodoInicio());
        assertEquals(LocalDate.of(2026, 1, 31), parsed.periodoFim());
        assertEquals(3, parsed.lineCountDeclared());
        assertEquals(3, parsed.lineCountProcessed());
    }

    @Test
    void parse_sem_0000_lanca_erro() {
        String content = """
                |0001|0|
                |9999|2|
                """;

        assertThrows(ValidationException.class,
                () -> parser.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void parse_qtd_linhas_divergente_lanca_erro() {
        String content = """
                |0000|020|0|01012026|31012026|EMPRESA|12345678000199|SP|110042490114|
                |0001|0|
                |9999|2|
                """;

        assertThrows(ValidationException.class,
                () -> parser.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }
}

