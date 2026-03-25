package br.com.techbr.fiscalanalyzer.identity.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_user_empresa")
public class AppUserEmpresa {

    @EmbeddedId
    private AppUserEmpresaId id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public AppUserEmpresaId getId() {
        return id;
    }

    public void setId(AppUserEmpresaId id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
