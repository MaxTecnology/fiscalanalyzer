package br.com.techbr.fiscalanalyzer.identity.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_user_role")
public class AppUserRole {

    @EmbeddedId
    private AppUserRoleId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public AppUserRoleId getId() {
        return id;
    }

    public void setId(AppUserRoleId id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
