package br.com.techbr.fiscalanalyzer.xml.parser;

import br.com.techbr.fiscalanalyzer.xml.model.ParsedDocument;

import java.io.InputStream;

public class NFeXmlParser implements XmlParser {

    @Override
    public ParsedDocument parse(InputStream inputStream) {
        // TODO: implementar parsing streaming (StAX)
        throw new UnsupportedOperationException("NFeXmlParser not implemented yet");
    }
}
