package br.com.techbr.fiscalanalyzer.sped.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sped_fiscal_file")
public class SpedFiscalFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SpedFiscalFileSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SpedFiscalFileStatus status;

    @Column(name = "original_file_name", nullable = false, columnDefinition = "text")
    private String originalFileName;

    @Column(name = "storage_object_key", nullable = false, columnDefinition = "text")
    private String storageObjectKey;

    @Column(name = "zip_entry_name", columnDefinition = "text")
    private String zipEntryName;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "content_size")
    private Long contentSize;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "layout_version", length = 10)
    private String layoutVersion;

    @Column(name = "periodo_inicio")
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim")
    private LocalDate periodoFim;

    @Column(name = "line_count_declared")
    private Integer lineCountDeclared;

    @Column(name = "line_count_processed")
    private Integer lineCountProcessed;

    @Column(name = "erro_codigo", length = 80)
    private String erroCodigo;

    @Column(name = "erro_mensagem", columnDefinition = "text")
    private String erroMensagem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = SpedFiscalFileStatus.PENDENTE_PARSE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Long empresaId) {
        this.empresaId = empresaId;
    }

    public SpedFiscalFileSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SpedFiscalFileSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public SpedFiscalFileStatus getStatus() {
        return status;
    }

    public void setStatus(SpedFiscalFileStatus status) {
        this.status = status;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public void setStorageObjectKey(String storageObjectKey) {
        this.storageObjectKey = storageObjectKey;
    }

    public String getZipEntryName() {
        return zipEntryName;
    }

    public void setZipEntryName(String zipEntryName) {
        this.zipEntryName = zipEntryName;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public Long getContentSize() {
        return contentSize;
    }

    public void setContentSize(Long contentSize) {
        this.contentSize = contentSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getLayoutVersion() {
        return layoutVersion;
    }

    public void setLayoutVersion(String layoutVersion) {
        this.layoutVersion = layoutVersion;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public void setPeriodoInicio(LocalDate periodoInicio) {
        this.periodoInicio = periodoInicio;
    }

    public LocalDate getPeriodoFim() {
        return periodoFim;
    }

    public void setPeriodoFim(LocalDate periodoFim) {
        this.periodoFim = periodoFim;
    }

    public Integer getLineCountDeclared() {
        return lineCountDeclared;
    }

    public void setLineCountDeclared(Integer lineCountDeclared) {
        this.lineCountDeclared = lineCountDeclared;
    }

    public Integer getLineCountProcessed() {
        return lineCountProcessed;
    }

    public void setLineCountProcessed(Integer lineCountProcessed) {
        this.lineCountProcessed = lineCountProcessed;
    }

    public String getErroCodigo() {
        return erroCodigo;
    }

    public void setErroCodigo(String erroCodigo) {
        this.erroCodigo = erroCodigo;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public void setErroMensagem(String erroMensagem) {
        this.erroMensagem = erroMensagem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

