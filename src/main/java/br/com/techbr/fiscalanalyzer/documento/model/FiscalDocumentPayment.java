package br.com.techbr.fiscalanalyzer.documento.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fiscal_document_payment")
public class FiscalDocumentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private FiscalDocument document;

    @Column(name = "payment_indicator")
    private Short paymentIndicator;

    @Column(name = "payment_type", length = 4)
    private String paymentType;

    @Column(name = "payment_amount", precision = 15, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "card_integration_type")
    private Short cardIntegrationType;

    @Column(name = "card_cnpj", length = 14)
    private String cardCnpj;

    @Column(name = "card_brand", length = 2)
    private String cardBrand;

    @Column(name = "card_authorization_code", length = 50)
    private String cardAuthorizationCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public FiscalDocument getDocument() { return document; }
    public void setDocument(FiscalDocument document) { this.document = document; }
    public Short getPaymentIndicator() { return paymentIndicator; }
    public void setPaymentIndicator(Short paymentIndicator) { this.paymentIndicator = paymentIndicator; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public void setPaymentAmount(BigDecimal paymentAmount) { this.paymentAmount = paymentAmount; }
    public Short getCardIntegrationType() { return cardIntegrationType; }
    public void setCardIntegrationType(Short cardIntegrationType) { this.cardIntegrationType = cardIntegrationType; }
    public String getCardCnpj() { return cardCnpj; }
    public void setCardCnpj(String cardCnpj) { this.cardCnpj = cardCnpj; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getCardAuthorizationCode() { return cardAuthorizationCode; }
    public void setCardAuthorizationCode(String cardAuthorizationCode) { this.cardAuthorizationCode = cardAuthorizationCode; }
}
