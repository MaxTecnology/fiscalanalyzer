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
            Integer numeroNota = null;
            String serie = null;
            String naturezaOperacao = null;
            String tpNF = null;
            LocalDate issueDate = null;
            Instant issueDateTime = null;
            Short ambiente = null;
            Short finalidadeEmissao = null;
            Short consumidorFinal = null;
            Short presencaComprador = null;
            String emitCnpj = null;
            String emitName = null;
            String emitIe = null;
            String emitUf = null;
            String emitMunicipio = null;
            String destDocument = null;
            String destCnpj = null;
            String destName = null;
            String destIe = null;
            String destUf = null;
            String destMunicipio = null;
            BigDecimal totalProducts = null;
            BigDecimal totalAmount = null;
            BigDecimal totalFrete = null;
            BigDecimal totalDesconto = null;
            BigDecimal totalOutros = null;
            BigDecimal totalIpi = null;
            BigDecimal totalIss = null;
            BigDecimal totalTributos = null;
            BigDecimal totalIcms = null;
            BigDecimal totalPis = null;
            BigDecimal totalCofins = null;
            String protocoloNumero = null;
            Integer protocoloStatus = null;
            String protocoloMotivo = null;
            Instant protocoloRecebimento = null;
            String qrCodeUrl = null;
            String faturaNumero = null;
            BigDecimal faturaValorOriginal = null;
            BigDecimal faturaValorDesconto = null;
            BigDecimal faturaValorLiquido = null;

            List<ParsedNfePayment> payments = new ArrayList<>();
            List<ParsedNfeDuplicate> duplicates = new ArrayList<>();
            List<ParsedNfeAdditionalInfo> additionalInfos = new ArrayList<>();

            List<ParsedNfeItem> items = new ArrayList<>();
            ParsedItemBuilder currentItem = null;
            ParsedPaymentBuilder currentPayment = null;
            ParsedDuplicateBuilder currentDuplicate = null;
            ParsedAdditionalInfoBuilder currentAdditionalInfo = null;
            boolean inProd = false;
            boolean inICMS = false;
            boolean inPIS = false;
            boolean inCOFINS = false;
            boolean inCard = false;

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
                    if ("chNFe".equals(name) && accessKey == null) {
                        String normalized = normalizeAccessKey(reader.getElementText());
                        if (normalized != null) {
                            accessKey = normalized;
                        }
                        stack.pop();
                        continue;
                    }

                    if ("mod".equals(name)) {
                        model = parseShort(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("nNF".equals(name)) {
                        numeroNota = parseInteger(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("serie".equals(name)) {
                        String value = reader.getElementText();
                        serie = value == null ? null : value.trim();
                        stack.pop();
                        continue;
                    }
                    if ("natOp".equals(name)) {
                        String value = reader.getElementText();
                        naturezaOperacao = value == null ? null : value.trim();
                        stack.pop();
                        continue;
                    }
                    if ("tpNF".equals(name)) {
                        tpNF = reader.getElementText();
                        stack.pop();
                        continue;
                    }
                    if ("tpAmb".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ide".equals(parent)) {
                            ambiente = parseShort(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("finNFe".equals(name)) {
                        finalidadeEmissao = parseShort(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("indFinal".equals(name)) {
                        consumidorFinal = parseShort(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("indPres".equals(name)) {
                        presencaComprador = parseShort(reader.getElementText());
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
                    if ("CNPJ".equals(name) || "CPF".equals(name)) {
                        String parent = parentOf(stack);
                        String value = normalizeDocument(reader.getElementText());
                        if ("emit".equals(parent) && emitCnpj == null) {
                            emitCnpj = value;
                            stack.pop();
                            continue;
                        }
                        if ("dest".equals(parent)) {
                            if (destDocument == null) {
                                destDocument = value;
                            }
                            if (destCnpj == null) {
                                destCnpj = value;
                            }
                            stack.pop();
                            continue;
                        }
                        if ("card".equals(parent) && inCard && currentPayment != null) {
                            currentPayment.cardCnpj = value;
                            stack.pop();
                            continue;
                        }
                    }
                    if ("xNome".equals(name)) {
                        String parent = parentOf(stack);
                        if ("emit".equals(parent) && emitName == null) {
                            emitName = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                        if ("dest".equals(parent) && destName == null) {
                            destName = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("IE".equals(name)) {
                        String parent = parentOf(stack);
                        if ("emit".equals(parent) && emitIe == null) {
                            emitIe = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                        if ("dest".equals(parent) && destIe == null) {
                            destIe = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("UF".equals(name)) {
                        String parent = parentOf(stack);
                        if ("enderEmit".equals(parent) && emitUf == null) {
                            emitUf = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                        if ("enderDest".equals(parent) && destUf == null) {
                            destUf = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("xMun".equals(name)) {
                        String parent = parentOf(stack);
                        if ("enderEmit".equals(parent) && emitMunicipio == null) {
                            emitMunicipio = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                        if ("enderDest".equals(parent) && destMunicipio == null) {
                            destMunicipio = trimOrNull(reader.getElementText());
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
                    if ("vFrete".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalFrete = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vDesc".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalDesconto = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                        if ("fat".equals(parent)) {
                            faturaValorDesconto = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vOutro".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalOutros = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vIPI".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalIpi = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vISS".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalIss = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vTotTrib".equals(name)) {
                        String parent = parentOf(stack);
                        if ("ICMSTot".equals(parent)) {
                            totalTributos = parseBigDecimal(reader.getElementText());
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
                    if ("nProt".equals(name)) {
                        String parent = parentOf(stack);
                        if ("infProt".equals(parent)) {
                            protocoloNumero = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("cStat".equals(name)) {
                        String parent = parentOf(stack);
                        if ("infProt".equals(parent)) {
                            protocoloStatus = parseInteger(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("xMotivo".equals(name)) {
                        String parent = parentOf(stack);
                        if ("infProt".equals(parent)) {
                            protocoloMotivo = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("dhRecbto".equals(name)) {
                        String parent = parentOf(stack);
                        if ("infProt".equals(parent)) {
                            protocoloRecebimento = parseOffsetInstant(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("qrCode".equals(name)) {
                        qrCodeUrl = trimOrNull(reader.getElementText());
                        stack.pop();
                        continue;
                    }
                    if ("nFat".equals(name)) {
                        String parent = parentOf(stack);
                        if ("fat".equals(parent)) {
                            faturaNumero = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vOrig".equals(name)) {
                        String parent = parentOf(stack);
                        if ("fat".equals(parent)) {
                            faturaValorOriginal = parseBigDecimal(reader.getElementText());
                            stack.pop();
                            continue;
                        }
                    }
                    if ("vLiq".equals(name)) {
                        String parent = parentOf(stack);
                        if ("fat".equals(parent)) {
                            faturaValorLiquido = parseBigDecimal(reader.getElementText());
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
                    if ("detPag".equals(name)) {
                        currentPayment = new ParsedPaymentBuilder();
                        continue;
                    }
                    if ("card".equals(name)) {
                        inCard = true;
                        continue;
                    }
                    if ("dup".equals(name)) {
                        currentDuplicate = new ParsedDuplicateBuilder();
                        continue;
                    }
                    if ("obsCont".equals(name)) {
                        currentAdditionalInfo = new ParsedAdditionalInfoBuilder();
                        currentAdditionalInfo.infoType = "OBS_CONT";
                        currentAdditionalInfo.fieldName = trimOrNull(reader.getAttributeValue(null, "xCampo"));
                        continue;
                    }
                    if ("infCpl".equals(name)) {
                        String value = trimOrNull(reader.getElementText());
                        if (value != null) {
                            additionalInfos.add(new ParsedNfeAdditionalInfo("INF_CPL", null, value));
                        }
                        stack.pop();
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
                                case "uCom" -> {
                                    currentItem.unidade = trimOrNull(reader.getElementText());
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
                    if (currentPayment != null) {
                        switch (name) {
                            case "indPag" -> {
                                currentPayment.paymentIndicator = parseShort(reader.getElementText());
                                stack.pop();
                                continue;
                            }
                            case "tPag" -> {
                                currentPayment.paymentType = trimOrNull(reader.getElementText());
                                stack.pop();
                                continue;
                            }
                            case "vPag" -> {
                                currentPayment.paymentAmount = parseBigDecimal(reader.getElementText());
                                stack.pop();
                                continue;
                            }
                            case "tpIntegra" -> {
                                if (inCard) {
                                    currentPayment.cardIntegrationType = parseShort(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                            }
                            case "tBand" -> {
                                if (inCard) {
                                    currentPayment.cardBrand = trimOrNull(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                            }
                            case "cAut" -> {
                                if (inCard) {
                                    currentPayment.cardAuthorizationCode = trimOrNull(reader.getElementText());
                                    stack.pop();
                                    continue;
                                }
                            }
                        }
                    }
                    if (currentDuplicate != null) {
                        switch (name) {
                            case "nDup" -> {
                                currentDuplicate.duplicateNumber = trimOrNull(reader.getElementText());
                                stack.pop();
                                continue;
                            }
                            case "dVenc" -> {
                                currentDuplicate.dueDate = parseDate(reader.getElementText());
                                stack.pop();
                                continue;
                            }
                            case "vDup" -> {
                                currentDuplicate.amount = parseBigDecimal(reader.getElementText());
                                stack.pop();
                                continue;
                            }
                        }
                    }
                    if (currentAdditionalInfo != null) {
                        if ("xTexto".equals(name)) {
                            currentAdditionalInfo.textValue = trimOrNull(reader.getElementText());
                            stack.pop();
                            continue;
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
                    } else if ("card".equals(name)) {
                        inCard = false;
                    } else if ("detPag".equals(name) && currentPayment != null) {
                        ParsedNfePayment parsedPayment = currentPayment.toParsedPayment();
                        if (parsedPayment.paymentType() != null || parsedPayment.paymentAmount() != null) {
                            payments.add(parsedPayment);
                        }
                        currentPayment = null;
                    } else if ("dup".equals(name) && currentDuplicate != null) {
                        ParsedNfeDuplicate parsedDuplicate = currentDuplicate.toParsedDuplicate();
                        if (parsedDuplicate.duplicateNumber() != null
                                || parsedDuplicate.dueDate() != null
                                || parsedDuplicate.amount() != null) {
                            duplicates.add(parsedDuplicate);
                        }
                        currentDuplicate = null;
                    } else if ("obsCont".equals(name) && currentAdditionalInfo != null) {
                        ParsedNfeAdditionalInfo parsedAdditionalInfo = currentAdditionalInfo.toParsedAdditionalInfo();
                        if (parsedAdditionalInfo.textValue() != null) {
                            additionalInfos.add(parsedAdditionalInfo);
                        }
                        currentAdditionalInfo = null;
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
                    numeroNota,
                    serie,
                    naturezaOperacao,
                    issueDate,
                    issueDateTime,
                    ambiente,
                    finalidadeEmissao,
                    consumidorFinal,
                    presencaComprador,
                    operationType,
                    emitCnpj,
                    emitName,
                    emitIe,
                    emitUf,
                    emitMunicipio,
                    destDocument,
                    destCnpj,
                    destName,
                    destIe,
                    destUf,
                    destMunicipio,
                    totalProducts,
                    totalAmount,
                    totalFrete,
                    totalDesconto,
                    totalOutros,
                    totalIpi,
                    totalIss,
                    totalTributos,
                    totalIcms,
                    totalPis,
                    totalCofins,
                    protocoloNumero,
                    protocoloStatus,
                    protocoloMotivo,
                    protocoloRecebimento,
                    qrCodeUrl,
                    faturaNumero,
                    faturaValorOriginal,
                    faturaValorDesconto,
                    faturaValorLiquido,
                    payments,
                    duplicates,
                    additionalInfos,
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
        value = value.replaceAll("\\D", "");
        if (value.length() == 44) {
            return value;
        }
        return null;
    }

    private String normalizeDocument(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\D", "");
        if (normalized.isEmpty()) return null;
        return normalized;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Short parseShort(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return Short.parseShort(normalized);
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return Integer.parseInt(normalized);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }
        return new BigDecimal(normalized);
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
        String normalized = tpNF == null ? null : tpNF.trim();
        if (Objects.equals(normalized, "0")) return "E";
        if (Objects.equals(normalized, "1")) return "S";
        throw new ValidationException("operation_type ausente");
    }

    private static final class ParsedItemBuilder {
        Integer itemNumber;
        String productCode;
        String productDescription;
        String ncm;
        String cfop;
        String unidade;
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
                    unidade,
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

    private static final class ParsedPaymentBuilder {
        Short paymentIndicator;
        String paymentType;
        BigDecimal paymentAmount;
        Short cardIntegrationType;
        String cardCnpj;
        String cardBrand;
        String cardAuthorizationCode;

        ParsedNfePayment toParsedPayment() {
            return new ParsedNfePayment(
                    paymentIndicator,
                    paymentType,
                    paymentAmount,
                    cardIntegrationType,
                    cardCnpj,
                    cardBrand,
                    cardAuthorizationCode
            );
        }
    }

    private static final class ParsedDuplicateBuilder {
        String duplicateNumber;
        LocalDate dueDate;
        BigDecimal amount;

        ParsedNfeDuplicate toParsedDuplicate() {
            return new ParsedNfeDuplicate(duplicateNumber, dueDate, amount);
        }
    }

    private static final class ParsedAdditionalInfoBuilder {
        String infoType;
        String fieldName;
        String textValue;

        ParsedNfeAdditionalInfo toParsedAdditionalInfo() {
            return new ParsedNfeAdditionalInfo(infoType, fieldName, textValue);
        }
    }
}
