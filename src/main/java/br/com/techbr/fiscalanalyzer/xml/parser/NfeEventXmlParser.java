package br.com.techbr.fiscalanalyzer.xml.parser;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

public class NfeEventXmlParser {

    private final XMLInputFactory factory = XMLInputFactory.newFactory();

    public NfeEventXmlParser() {
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    }

    public ParsedNfeEvent parse(InputStream inputStream) {
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            Deque<String> stack = new ArrayDeque<>();

            String eventId = null;
            String accessKey = null;
            String eventType = null;
            String eventDescription = null;
            Integer eventSequence = null;
            LocalDate eventDate = null;
            Instant eventDatetime = null;
            Instant eventRegistrationDatetime = null;
            Integer eventStatus = null;
            String eventStatusReason = null;
            String eventProtocol = null;
            String referenceProtocol = null;
            String authorCnpj = null;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    stack.push(name);

                    if ("infEvento".equals(name) && eventId == null) {
                        String rawId = reader.getAttributeValue(null, "Id");
                        eventId = trimOrNull(rawId);
                        continue;
                    }

                    if ("chNFe".equals(name)) {
                        String value = normalizeAccessKey(reader.getElementText());
                        if (value != null) {
                            accessKey = value;
                        }
                        stack.pop();
                        continue;
                    }

                    if ("tpEvento".equals(name)) {
                        String value = trimOrNull(reader.getElementText());
                        if (eventType == null) {
                            eventType = value;
                        }
                        stack.pop();
                        continue;
                    }

                    if ("nSeqEvento".equals(name)) {
                        Integer value = parseInteger(reader.getElementText());
                        if (value != null && eventSequence == null) {
                            eventSequence = value;
                        }
                        stack.pop();
                        continue;
                    }

                    if ("dhEvento".equals(name)) {
                        if (eventDatetime == null) {
                            String rawDateTime = reader.getElementText();
                            eventDatetime = parseOffsetInstant(rawDateTime);
                            eventDate = parseOffsetDate(rawDateTime);
                        } else {
                            reader.getElementText();
                        }
                        stack.pop();
                        continue;
                    }

                    if ("dhRegEvento".equals(name)) {
                        if (eventRegistrationDatetime == null) {
                            eventRegistrationDatetime = parseOffsetInstant(reader.getElementText());
                        } else {
                            reader.getElementText();
                        }
                        stack.pop();
                        continue;
                    }

                    if ("descEvento".equals(name)) {
                        String value = trimOrNull(reader.getElementText());
                        if (eventDescription == null) {
                            eventDescription = value;
                        }
                        stack.pop();
                        continue;
                    }

                    if ("cStat".equals(name) && hasAncestor(stack, "retEvento")) {
                        eventStatus = parseInteger(reader.getElementText());
                        stack.pop();
                        continue;
                    }

                    if ("xMotivo".equals(name) && hasAncestor(stack, "retEvento")) {
                        eventStatusReason = trimOrNull(reader.getElementText());
                        stack.pop();
                        continue;
                    }

                    if ("nProt".equals(name)) {
                        String parent = parentOf(stack);
                        String value = trimOrNull(reader.getElementText());
                        if ("detEvento".equals(parent)) {
                            referenceProtocol = value;
                        } else if (hasAncestor(stack, "retEvento")) {
                            eventProtocol = value;
                        }
                        stack.pop();
                        continue;
                    }

                    if ("CNPJ".equals(name) && hasAncestor(stack, "evento")) {
                        String value = normalizeDocument(reader.getElementText());
                        if (authorCnpj == null) {
                            authorCnpj = value;
                        }
                        stack.pop();
                        continue;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                }
            }

            if (eventId == null || eventId.isBlank()) {
                throw new ValidationException("event_id ausente");
            }
            if (accessKey == null || accessKey.length() != 44) {
                throw new ValidationException("access_key_event ausente");
            }
            short model = parseModelFromAccessKey(accessKey);
            if (model != 55 && model != 65) {
                throw new ValidationException("model_event invalido");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new ValidationException("event_type ausente");
            }
            if (eventSequence == null) {
                throw new ValidationException("event_sequence ausente");
            }
            if (eventDatetime == null) {
                throw new ValidationException("event_datetime ausente");
            }

            return new ParsedNfeEvent(
                    model,
                    accessKey,
                    eventId,
                    eventType,
                    eventDescription,
                    eventSequence,
                    eventDate,
                    eventDatetime,
                    eventRegistrationDatetime,
                    eventStatus,
                    eventStatusReason,
                    eventProtocol,
                    referenceProtocol,
                    authorCnpj
            );
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("xml_evento_invalido");
        }
    }

    private String normalizeAccessKey(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\D", "");
        if (normalized.length() != 44) return null;
        return normalized;
    }

    private short parseModelFromAccessKey(String accessKey) {
        return Short.parseShort(accessKey.substring(20, 22));
    }

    private String normalizeDocument(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\D", "");
        if (normalized.isBlank()) return null;
        return normalized;
    }

    private Instant parseOffsetInstant(String value) {
        if (value == null || value.isBlank()) return null;
        return OffsetDateTime.parse(value.trim()).toInstant();
    }

    private LocalDate parseOffsetDate(String value) {
        if (value == null || value.isBlank()) return null;
        return OffsetDateTime.parse(value.trim()).toLocalDate();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String parentOf(Deque<String> stack) {
        if (stack.size() < 2) return null;
        String current = stack.pop();
        String parent = stack.peek();
        stack.push(current);
        return parent;
    }

    private boolean hasAncestor(Deque<String> stack, String ancestor) {
        boolean skipCurrent = true;
        for (String element : stack) {
            if (skipCurrent) {
                skipCurrent = false;
                continue;
            }
            if (ancestor.equals(element)) {
                return true;
            }
        }
        return false;
    }
}
