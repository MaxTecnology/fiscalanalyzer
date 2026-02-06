package br.com.techbr.fiscalanalyzer.xml.parser;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NfeXmlParserTest {

    @Test
    void parse_nfe55_ok() {
        String xml = """
                <nfeProc>
                  <NFe>
                    <infNFe Id="NFe35191111111111111111550010000000011000000010">
                      <ide>
                        <mod>55</mod>
                        <tpNF>1</tpNF>
                        <dhEmi>2024-01-02T10:30:00-03:00</dhEmi>
                      </ide>
                      <emit><CNPJ>11111111111111</CNPJ></emit>
                      <dest><CNPJ>22222222222222</CNPJ></dest>
                      <total><ICMSTot><vNF>123.45</vNF></ICMSTot></total>
                    </infNFe>
                  </NFe>
                </nfeProc>
                """;
        NfeXmlParser parser = new NfeXmlParser();
        ParsedNfe parsed = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(55, parsed.model());
        assertEquals("35191111111111111111550010000000011000000010", parsed.accessKey());
        assertEquals("S", parsed.operationType());
        assertEquals("11111111111111", parsed.emitCnpj());
        assertEquals("22222222222222", parsed.destCnpj());
        assertEquals("123.45", parsed.totalAmount().toString());
        assertEquals(0, parsed.items().size());
        assertEquals(2024, parsed.issueDate().getYear());
    }

    @Test
    void parse_nfce65_ok() {
        String xml = """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <infNFe Id="NFe35191111111111111111650010000000011000000010">
                    <ide>
                      <mod>65</mod>
                      <tpNF>0</tpNF>
                      <dEmi>2024-02-01</dEmi>
                    </ide>
                    <emit><CNPJ>11111111111111</CNPJ></emit>
                    <total><ICMSTot><vNF>10.00</vNF></ICMSTot></total>
                  </infNFe>
                </NFe>
                """;
        NfeXmlParser parser = new NfeXmlParser();
        ParsedNfe parsed = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(65, parsed.model());
        assertEquals("E", parsed.operationType());
    }

    @Test
    void parse_faltaCampos_disparaErro() {
        String xml = "<NFe><infNFe></infNFe></NFe>";
        NfeXmlParser parser = new NfeXmlParser();
        assertThrows(ValidationException.class, () ->
                parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
        );
    }

    @Test
    void parse_comTotaisEItens() {
        String xml = """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <infNFe Id="NFe26251216404287029056550010001762721022736522">
                    <ide>
                      <mod>55</mod>
                      <tpNF>1</tpNF>
                      <dhEmi>2025-12-19T10:53:06-03:00</dhEmi>
                    </ide>
                    <emit><CNPJ>16404287029056</CNPJ></emit>
                    <dest><CNPJ>05841177000134</CNPJ></dest>
                    <det nItem="1">
                      <prod>
                        <cProd>0001</cProd>
                        <xProd>PRODUTO A</xProd>
                        <NCM>48025610</NCM>
                        <CFOP>6102</CFOP>
                        <qCom>1.0000</qCom>
                        <vUnCom>100.0000</vUnCom>
                        <vProd>100.00</vProd>
                      </prod>
                      <imposto>
                        <ICMS>
                          <ICMS00>
                            <CST>00</CST>
                            <vBC>100.00</vBC>
                            <pICMS>12.00</pICMS>
                            <vICMS>12.00</vICMS>
                          </ICMS00>
                        </ICMS>
                        <PIS>
                          <PISAliq>
                            <vBC>100.00</vBC>
                            <pPIS>1.65</pPIS>
                            <vPIS>1.65</vPIS>
                          </PISAliq>
                        </PIS>
                        <COFINS>
                          <COFINSAliq>
                            <vBC>100.00</vBC>
                            <pCOFINS>7.60</pCOFINS>
                            <vCOFINS>7.60</vCOFINS>
                          </COFINSAliq>
                        </COFINS>
                      </imposto>
                    </det>
                    <total>
                      <ICMSTot>
                        <vProd>85834.49</vProd>
                        <vICMS>10300.14</vICMS>
                        <vPIS>1246.32</vPIS>
                        <vCOFINS>5740.61</vCOFINS>
                        <vNF>88624.11</vNF>
                      </ICMSTot>
                    </total>
                  </infNFe>
                </NFe>
                """;
        NfeXmlParser parser = new NfeXmlParser();
        ParsedNfe parsed = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("85834.49", parsed.totalProducts().toString());
        assertEquals("10300.14", parsed.totalIcms().toString());
        assertEquals("1246.32", parsed.totalPis().toString());
        assertEquals("5740.61", parsed.totalCofins().toString());
        assertEquals(1, parsed.items().size());
        ParsedNfeItem item = parsed.items().get(0);
        assertEquals(1, item.itemNumber());
        assertEquals("0001", item.productCode());
        assertEquals("PRODUTO A", item.productDescription());
        assertEquals("48025610", item.ncm());
        assertEquals("6102", item.cfop());
        assertEquals("00", item.cstIcms());
        assertEquals("100.00", item.icmsBase().toString());
        assertEquals("12.00", item.icmsRate().toString());
        assertEquals("12.00", item.icmsValue().toString());
        assertEquals("100.00", item.pisBase().toString());
        assertEquals("1.65", item.pisRate().toString());
        assertEquals("1.65", item.pisValue().toString());
        assertEquals("100.00", item.cofinsBase().toString());
        assertEquals("7.60", item.cofinsRate().toString());
        assertEquals("7.60", item.cofinsValue().toString());
    }
}
