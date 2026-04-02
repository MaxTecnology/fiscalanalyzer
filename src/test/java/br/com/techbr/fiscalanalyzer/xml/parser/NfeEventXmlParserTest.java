package br.com.techbr.fiscalanalyzer.xml.parser;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NfeEventXmlParserTest {

    @Test
    void parse_evento_cancelamento_ok() throws Exception {
        String xml = Files.readString(Path.of("docs/xmls/eventoNFe.xml"));
        NfeEventXmlParser parser = new NfeEventXmlParser();

        ParsedNfeEvent parsed = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals((short) 65, parsed.model());
        assertEquals("27240629422082000144653010001324061620508982", parsed.accessKey());
        assertEquals("ID1101112724062942208200014465301000132406162050898201", parsed.eventId());
        assertEquals("110111", parsed.eventType());
        assertEquals("Cancelamento", parsed.eventDescription());
        assertEquals(Integer.valueOf(1), parsed.eventSequence());
        assertEquals(LocalDate.of(2024, 6, 20), parsed.eventDate());
        assertEquals(Integer.valueOf(135), parsed.eventStatus());
        assertEquals("Evento registrado e vinculado a NF-e", parsed.eventStatusReason());
        assertEquals("227240040314839", parsed.eventProtocol());
        assertEquals("227240040307925", parsed.referenceProtocol());
        assertEquals("29422082000144", parsed.authorCnpj());
    }

    @Test
    void parse_evento_sem_chave_disparaErro() {
        String xml = """
                <procEventoNFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <evento>
                    <infEvento Id="ID110111">
                      <tpEvento>110111</tpEvento>
                      <nSeqEvento>1</nSeqEvento>
                      <dhEvento>2024-06-20T18:02:27-03:00</dhEvento>
                    </infEvento>
                  </evento>
                </procEventoNFe>
                """;
        NfeEventXmlParser parser = new NfeEventXmlParser();

        assertThrows(ValidationException.class, () ->
                parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
        );
    }
}

