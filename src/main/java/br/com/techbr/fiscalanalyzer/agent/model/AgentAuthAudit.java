package br.com.techbr.fiscalanalyzer.agent.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "agent_auth_audit")
public class AgentAuthAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_scope", nullable = false, length = 20)
    private AgentAuthAuditScope authScope;

    @Column(name = "route", nullable = false, length = 120)
    private String route;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(name = "key_prefix", length = 20)
    private String keyPrefix;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private AgentAuthAuditResult result;

    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void setAuthScope(AgentAuthAuditScope authScope) {
        this.authScope = authScope;
    }

    public AgentAuthAuditScope getAuthScope() {
        return authScope;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getRoute() {
        return route;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setEmpresaId(Long empresaId) {
        this.empresaId = empresaId;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setResult(AgentAuthAuditResult result) {
        this.result = result;
    }

    public AgentAuthAuditResult getResult() {
        return result;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
