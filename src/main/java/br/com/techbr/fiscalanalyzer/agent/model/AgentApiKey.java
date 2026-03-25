package br.com.techbr.fiscalanalyzer.agent.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "agent_api_key",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_agent_api_key_hash", columnNames = "key_hash")
        }
)
public class AgentApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ultimo_uso_at")
    private Instant ultimoUsoAt;

    @Column(name = "criado_at", nullable = false)
    private Instant criadoAt;

    @Column(name = "revogado_at")
    private Instant revogadoAt;

    @Column(name = "criado_por", length = 100)
    private String criadoPor;

    @PrePersist
    void prePersist() {
        if (criadoAt == null) criadoAt = Instant.now();
        if (ativo == null) ativo = true;
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

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getUltimoUsoAt() {
        return ultimoUsoAt;
    }

    public void setUltimoUsoAt(Instant ultimoUsoAt) {
        this.ultimoUsoAt = ultimoUsoAt;
    }

    public Instant getCriadoAt() {
        return criadoAt;
    }

    public Instant getRevogadoAt() {
        return revogadoAt;
    }

    public void setRevogadoAt(Instant revogadoAt) {
        this.revogadoAt = revogadoAt;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public void setCriadoPor(String criadoPor) {
        this.criadoPor = criadoPor;
    }
}
