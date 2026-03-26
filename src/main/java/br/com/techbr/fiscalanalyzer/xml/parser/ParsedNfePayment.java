package br.com.techbr.fiscalanalyzer.xml.parser;

import java.math.BigDecimal;

public record ParsedNfePayment(
        Short paymentIndicator,
        String paymentType,
        BigDecimal paymentAmount,
        Short cardIntegrationType,
        String cardCnpj,
        String cardBrand,
        String cardAuthorizationCode
) {}
