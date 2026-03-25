package br.com.techbr.fiscalanalyzer.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AppUserEmpresaId implements Serializable {

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    public AppUserEmpresaId() {
    }

    public AppUserEmpresaId(Long appUserId, Long empresaId) {
        this.appUserId = appUserId;
        this.empresaId = empresaId;
    }

    public Long getAppUserId() {
        return appUserId;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUserEmpresaId that)) return false;
        return Objects.equals(appUserId, that.appUserId) && Objects.equals(empresaId, that.empresaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appUserId, empresaId);
    }
}
