package br.com.techbr.fiscalanalyzer.documento.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "fiscal_document_registry",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_registry_tenant_empresa_access",
                        columnNames = {"tenant_id", "empresa_id", "access_key"}
                )
        }
)
public class FiscalDocumentRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "access_key", nullable = false, length = 44)
    private String accessKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_document_id")
    private FiscalDocument fiscalDocument;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters/setters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public FiscalDocument getFiscalDocument() { return fiscalDocument; }
    public void setFiscalDocument(FiscalDocument fiscalDocument) { this.fiscalDocument = fiscalDocument; }
    public Instant getCreatedAt() { return createdAt; }
}
