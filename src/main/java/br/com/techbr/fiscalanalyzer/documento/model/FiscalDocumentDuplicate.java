package br.com.techbr.fiscalanalyzer.documento.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fiscal_document_duplicate")
public class FiscalDocumentDuplicate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private FiscalDocument document;

    @Column(name = "duplicate_number", length = 60)
    private String duplicateNumber;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public FiscalDocument getDocument() { return document; }
    public void setDocument(FiscalDocument document) { this.document = document; }
    public String getDuplicateNumber() { return duplicateNumber; }
    public void setDuplicateNumber(String duplicateNumber) { this.duplicateNumber = duplicateNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
