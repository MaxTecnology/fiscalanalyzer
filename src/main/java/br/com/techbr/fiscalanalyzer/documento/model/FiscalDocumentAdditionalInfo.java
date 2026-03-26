package br.com.techbr.fiscalanalyzer.documento.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "fiscal_document_additional_info")
public class FiscalDocumentAdditionalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private FiscalDocument document;

    @Column(name = "info_type", nullable = false, length = 20)
    private String infoType;

    @Column(name = "field_name", length = 60)
    private String fieldName;

    @Column(name = "text_value", nullable = false, columnDefinition = "text")
    private String textValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public FiscalDocument getDocument() { return document; }
    public void setDocument(FiscalDocument document) { this.document = document; }
    public String getInfoType() { return infoType; }
    public void setInfoType(String infoType) { this.infoType = infoType; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
}
