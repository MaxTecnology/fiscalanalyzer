package br.com.techbr.fiscalanalyzer.documento.model;

import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fiscal_document")
public class FiscalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "model", nullable = false)
    private Short model; // 55 ou 65

    @Column(name = "access_key", nullable = false, length = 44)
    private String accessKey;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "issue_datetime")
    private Instant issueDateTime;

    @Column(name = "operation_type", nullable = false, length = 1)
    private String operationType; // 'E' ou 'S'

    @Column(name = "emit_cnpj", nullable = false, length = 14)
    private String emitCnpj;

    @Column(name = "dest_cnpj", length = 14)
    private String destCnpj;

    @Column(name = "total_products", precision = 15, scale = 2)
    private BigDecimal totalProducts;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_icms", precision = 15, scale = 2)
    private BigDecimal totalIcms;

    @Column(name = "total_pis", precision = 15, scale = 2)
    private BigDecimal totalPis;

    @Column(name = "total_cofins", precision = 15, scale = 2)
    private BigDecimal totalCofins;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacao_id")
    private Importacao importacao;

    @Column(name = "xml_path", nullable = false, columnDefinition = "text")
    private String xmlPath;

    @Column(name = "xml_hash", nullable = false, length = 64)
    private String xmlHash;

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
    public Short getModel() { return model; }
    public void setModel(Short model) { this.model = model; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public Instant getIssueDateTime() { return issueDateTime; }
    public void setIssueDateTime(Instant issueDateTime) { this.issueDateTime = issueDateTime; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getEmitCnpj() { return emitCnpj; }
    public void setEmitCnpj(String emitCnpj) { this.emitCnpj = emitCnpj; }
    public String getDestCnpj() { return destCnpj; }
    public void setDestCnpj(String destCnpj) { this.destCnpj = destCnpj; }
    public BigDecimal getTotalProducts() { return totalProducts; }
    public void setTotalProducts(BigDecimal totalProducts) { this.totalProducts = totalProducts; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getTotalIcms() { return totalIcms; }
    public void setTotalIcms(BigDecimal totalIcms) { this.totalIcms = totalIcms; }
    public BigDecimal getTotalPis() { return totalPis; }
    public void setTotalPis(BigDecimal totalPis) { this.totalPis = totalPis; }
    public BigDecimal getTotalCofins() { return totalCofins; }
    public void setTotalCofins(BigDecimal totalCofins) { this.totalCofins = totalCofins; }
    public Importacao getImportacao() { return importacao; }
    public void setImportacao(Importacao importacao) { this.importacao = importacao; }
    public String getXmlPath() { return xmlPath; }
    public void setXmlPath(String xmlPath) { this.xmlPath = xmlPath; }
    public String getXmlHash() { return xmlHash; }
    public void setXmlHash(String xmlHash) { this.xmlHash = xmlHash; }
    public Instant getCreatedAt() { return createdAt; }
}
