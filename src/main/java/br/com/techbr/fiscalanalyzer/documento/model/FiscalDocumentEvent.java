package br.com.techbr.fiscalanalyzer.documento.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;

@Entity
@Table(name = "fiscal_document_event")
public class FiscalDocumentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_document_id")
    private FiscalDocument fiscalDocument;

    @Column(name = "access_key", nullable = false, length = 44)
    private String accessKey;

    @Column(name = "model", nullable = false)
    private Short model;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 10)
    private String eventType;

    @Column(name = "event_description", length = 120)
    private String eventDescription;

    @Column(name = "event_sequence")
    private Integer eventSequence;

    @Column(name = "event_datetime")
    private Instant eventDatetime;

    @Column(name = "event_registration_datetime")
    private Instant eventRegistrationDatetime;

    @Column(name = "event_status")
    private Integer eventStatus;

    @Column(name = "event_status_reason", columnDefinition = "text")
    private String eventStatusReason;

    @Column(name = "event_protocol", length = 32)
    private String eventProtocol;

    @Column(name = "reference_protocol", length = 32)
    private String referenceProtocol;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "author_cnpj", length = 14)
    private String authorCnpj;

    @Column(name = "xml_path", nullable = false, columnDefinition = "text")
    private String xmlPath;

    @Column(name = "xml_hash", nullable = false, length = 64)
    private String xmlHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }

    public FiscalDocument getFiscalDocument() { return fiscalDocument; }
    public void setFiscalDocument(FiscalDocument fiscalDocument) { this.fiscalDocument = fiscalDocument; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public Short getModel() { return model; }
    public void setModel(Short model) { this.model = model; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }

    public Integer getEventSequence() { return eventSequence; }
    public void setEventSequence(Integer eventSequence) { this.eventSequence = eventSequence; }

    public Instant getEventDatetime() { return eventDatetime; }
    public void setEventDatetime(Instant eventDatetime) { this.eventDatetime = eventDatetime; }

    public Instant getEventRegistrationDatetime() { return eventRegistrationDatetime; }
    public void setEventRegistrationDatetime(Instant eventRegistrationDatetime) {
        this.eventRegistrationDatetime = eventRegistrationDatetime;
    }

    public Integer getEventStatus() { return eventStatus; }
    public void setEventStatus(Integer eventStatus) { this.eventStatus = eventStatus; }

    public String getEventStatusReason() { return eventStatusReason; }
    public void setEventStatusReason(String eventStatusReason) { this.eventStatusReason = eventStatusReason; }

    public String getEventProtocol() { return eventProtocol; }
    public void setEventProtocol(String eventProtocol) { this.eventProtocol = eventProtocol; }

    public String getReferenceProtocol() { return referenceProtocol; }
    public void setReferenceProtocol(String referenceProtocol) { this.referenceProtocol = referenceProtocol; }

    public String getAuthorCnpj() { return authorCnpj; }
    public void setAuthorCnpj(String authorCnpj) { this.authorCnpj = authorCnpj; }

    public String getXmlPath() { return xmlPath; }
    public void setXmlPath(String xmlPath) { this.xmlPath = xmlPath; }

    public String getXmlHash() { return xmlHash; }
    public void setXmlHash(String xmlHash) { this.xmlHash = xmlHash; }

    public Instant getCreatedAt() { return createdAt; }
}
