package br.com.techbr.fiscalanalyzer.xml.parser;

import java.time.Instant;
import java.time.LocalDate;

public record ParsedNfeEvent(
        short model,
        String accessKey,
        String eventId,
        String eventType,
        String eventDescription,
        Integer eventSequence,
        LocalDate eventDate,
        Instant eventDatetime,
        Instant eventRegistrationDatetime,
        Integer eventStatus,
        String eventStatusReason,
        String eventProtocol,
        String referenceProtocol,
        String authorCnpj
) {}
