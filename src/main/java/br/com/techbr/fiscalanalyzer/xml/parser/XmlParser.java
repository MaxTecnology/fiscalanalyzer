package br.com.techbr.fiscalanalyzer.xml.parser;

import br.com.techbr.fiscalanalyzer.xml.model.ParsedDocument;

import java.io.InputStream;

public interface XmlParser {
    ParsedDocument parse(InputStream inputStream);
}
