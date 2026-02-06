package br.com.techbr.fiscalanalyzer.xml.parser;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class NfeXmlParser {

    private final XMLInputFactory factory = XMLInputFactory.newFactory();

    public NfeXmlParser() {
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    }

    public ParsedNfe parse(InputStream inputStream) {
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            Deque<String> stack = new ArrayDeque<>();

            String accessKey = null;
            Short model = null;
            String tpNF = null;
            LocalDate issueDate = null;
            Instant issueDateTime = null;
            String emitCnpj = null;
            String destCnpj = null;
            BigDecimal totalProducts = null;
            BigDecimal totalAmount = null;
            BigDecimal totalIcms = null;
            BigDecimal totalPis = null;
            BigDecimal totalCofins = null;

            List<ParsedNfeItem> items = new ArrayList<>();
            ParsedItemBuilder currentItem = null;
            boolean inProd = false;
            boolean inICMS = false;
            boolean inPIS = false;
            boolean inCOFINS = false;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    stack.push(name);

                    if ("infNFe".equals(name)) {
                        String id = reader.getAttributeValue(null, "Id");
                        if (id != null) {
                            String normalized = normalizeAccessKey(id);
                            if (normalized != null) {
                                accessKey = normalized;
                            }
                        }
                        continue;
                    }

                    if ("mod".equals(name)) {
                        model = parseShort(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("tpNF".equals(name)) {
                        tpNF = reader.getElementText();
                        stack.pop();
                        continue;
                    }
                    if ("dhEmi".equals(name)) {
                        String value = reader.getElementText();
                        issueDateTime = parseOffsetInstant(value);
                        issueDate = parseDate(value);
                        stack.pop();
                        continue;
                    }
                    if ("dEmi".equals(name) && issueDate == null) {
                        issueDate = parseDate(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("CNPJ".equals(name)) {
                        String parent = parentOf(stack);
                        if ("emit".equals(parent) && emitCnpj == null) {
                            emitCnpj = reader.getElementText();
                            stack.pop();
                            continue;
                        }
                        if ("dest".equals(parent) && destCnpj == null) {
                            destCnpj = reader.getElementText();
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vNF".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalAmount = parseBigDecimal(reader.getElementText());
                            stack.pop();
                        }
                        continue;
                    }
                    if ("vProd".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalProducts = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vICMS".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalIcms = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vPIS".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalPis = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vCOFINS".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalCofins = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }

                    if ("det".equals(name)) {
                        currentItem = new ParsedItemBuilder();
                        String nItem = reader.getAttributeValue(null, "nItem");
                        if (nItem != null && !nItem.isBlank()) {
                            currentItem.itemNumber = Integer.parseInt(nItem.trim());
                        }
                        continue;
                    }
                    if ("prod".equals(name)) {
                        inProd = true;
                        continue;
                    }
                    if ("ICMS".equals(name)) {
                        inICMS = true;
                        continue;
                    }
                    if ("PIS".equals(name)) {
                        inPIS = true;
                        continue;
                    }
                    if ("COFINS".equals(name)) {
                        inCOFINS = true;
                        continue;
                    }

                    if (currentItem != null) {
                        if (inProd) {
                            switch (name) {
                                case "cProd" -> {
                                    currentItem.productCode = reader.getElementText();
                                    stack.pop();
                                    continue;
                                }
                                case "xProd" -> {
                                    currentItem.productDescription = reader.getElementText();
                                    stack.pop();
                                    continue;
                                }
                                case "NCM" -> {
                                    currentItem.ncm = reader.getElementText();
                                    stack.pop();
                                    continue;
                                }
                                case "CFOP" -> {
                                    currentItem.cfop = reader.getElementText();
                                    stack.pop();
                                    continue;
                                }
                                case "qCom" -> {
                                    currentItem.quantity = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "vUnCom" -> {
                                    currentItem.unitPrice = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "vProd" -> {
                                    String parent = parentOf(stack);
                                    if ("prod".equals(parent)) {
                                        currentItem.totalValue = parseBigDecimal(reader.getElementText());
                                        stack.pop();
                                        continue;
                                    }
                                }
                            }
                        }
                        if (inICMS) {
                            switch (name) {
                                case "CST" -> {
                                    currentItem.cstIcms = reader.getElementText();
                                    stack.pop();
                                    continue;
                                }
                                case "CSOSN" -> {
                                    currentItem.csosn = reader.getElementText();
                                    stack.pop();
                                    continue;
                                }
                                case "vBC" -> {
                                    currentItem.icmsBase = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "pICMS" -> {
                                    currentItem.icmsRate = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "vICMS" -> {
                                    currentItem.icmsValue = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                            }
                        }
                        if (inPIS) {
                            switch (name) {
                                case "vBC" -> {
                                    currentItem.pisBase = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "pPIS" -> {
                                    currentItem.pisRate = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "vPIS" -> {
                                    currentItem.pisValue = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                            }
                        }
                        if (inCOFINS) {
                            switch (name) {
                                case "vBC" -> {
                                    currentItem.cofinsBase = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "pCOFINS" -> {
                                    currentItem.cofinsRate = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                                case "vCOFINS" -> {
                                    currentItem.cofinsValue = parseBigDecimal(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                            }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("det".equals(name) && currentItem != null) {
                        if (currentItem.itemNumber == null) {
                            throw new ValidationException("item_number ausente");
                        }
                        items.add(currentItem.toParsedItem());
                        currentItem = null;
                    } else if ("prod".equals(name)) {
                        inProd = false;
                    } else if ("ICMS".equals(name)) {
                        inICMS = false;
                    } else if ("PIS".equals(name)) {
                        inPIS = false;
                    } else if ("COFINS".equals(name)) {
                        inCOFINS = false;
                    }
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                }
            }

            if (accessKey == null || accessKey.length() != 44) {
                throw new ValidationException("access_key ausente");
            }
            if (model == null || !(model == 55 || model == 65)) {
                throw new ValidationException("model ausente");
            }
            if (issueDate == null) {
                throw new ValidationException("issue_date ausente");
            }
            if (emitCnpj == null || emitCnpj.isBlank()) {
                throw new ValidationException("emit_cnpj ausente");
            }
            if (totalAmount == null) {
                throw new ValidationException("total_amount ausente");
            }
            String operationType = mapTpNF(tpNF);

            return new ParsedNfe(
                    model,
                    accessKey,
                    issueDate,
                    issueDateTime,
                    operationType,
                    emitCnpj,
                    destCnpj,
                    totalProducts,
                    totalAmount,
                    totalIcms,
                    totalPis,
                    totalCofins,
                    items
            );
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("xml_invalido");
        }
    }

    private String parentOf(Deque<String> stack) {
        if (stack.size() < 2) return null;
        String current = stack.pop();
        String parent = stack.peek();
        stack.push(current);
        return parent;
    }

    private String normalizeAccessKey(String id) {
        String value = id.trim();
        if (value.startsWith("NFe")) {
            value = value.substring(3);
        }
        if (value.length() == 44) {
            return value;
        }
        return null;
    }

    private Short parseShort(String value) {
        if (value == null) return null;
        return Short.parseShort(value.trim());
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null) return null;
        return new BigDecimal(value.trim());
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(v).toLocalDate();
        } catch (Exception ignored) {
        }
        return LocalDate.parse(v);
    }

    private Instant parseOffsetInstant(String value) {
        if (value == null) return null;
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String mapTpNF(String tpNF) {
        if (Objects.equals(tpNF, "0")) return "E";
        if (Objects.equals(tpNF, "1")) return "S";
        throw new ValidationException("operation_type ausente");
    }

    private static final class ParsedItemBuilder {
        Integer itemNumber;
        String productCode;
        String productDescription;
        String ncm;
        String cfop;
        String cstIcms;
        String csosn;
        BigDecimal quantity;
        BigDecimal unitPrice;
        BigDecimal totalValue;
        BigDecimal icmsBase;
        BigDecimal icmsRate;
        BigDecimal icmsValue;
        BigDecimal pisBase;
        BigDecimal pisRate;
        BigDecimal pisValue;
        BigDecimal cofinsBase;
        BigDecimal cofinsRate;
        BigDecimal cofinsValue;

        ParsedNfeItem toParsedItem() {
            return new ParsedNfeItem(
                    itemNumber,
                    productCode,
                    productDescription,
                    ncm,
                    cfop,
                    cstIcms,
                    csosn,
                    quantity,
                    unitPrice,
                    totalValue,
                    icmsBase,
                    icmsRate,
                    icmsValue,
                    pisBase,
                    pisRate,
                    pisValue,
                    cofinsBase,
                    cofinsRate,
                    cofinsValue
            );
        }
    }
}
