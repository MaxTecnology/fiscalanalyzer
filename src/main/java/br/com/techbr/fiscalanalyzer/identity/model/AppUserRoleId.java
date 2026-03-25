package br.com.techbr.fiscalanalyzer.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AppUserRoleId implements Serializable {

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    @Column(name = "app_role_id", nullable = false)
    private Long appRoleId;

    public AppUserRoleId() {
    }

    public AppUserRoleId(Long appUserId, Long appRoleId) {
        this.appUserId = appUserId;
        this.appRoleId = appRoleId;
    }

    public Long getAppUserId() {
        return appUserId;
    }

    public Long getAppRoleId() {
        return appRoleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUserRoleId that)) return false;
        return Objects.equals(appUserId, that.appUserId) && Objects.equals(appRoleId, that.appRoleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appUserId, appRoleId);
    }
}
