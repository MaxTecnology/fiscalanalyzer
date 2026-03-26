package br.com.techbr.fiscalanalyzer.agent.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "agent_instance_status",
        uniqueConstraints = @UniqueConstraint(name = "uq_agent_instance_status_scope", columnNames = {"tenant_id", "empresa_id", "agent_id"})
)
public class AgentInstanceStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "key_prefix", length = 20)
    private String keyPrefix;

    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgentInstanceStatusType status = AgentInstanceStatusType.ONLINE;

    @Column(name = "agent_version", length = 60)
    private String agentVersion;

    @Column(name = "hostname", length = 120)
    private String hostname;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "uploads_in_flight")
    private Integer uploadsInFlight;

    @Column(name = "pending_files")
    private Integer pendingFiles;

    @Column(name = "error_count_window")
    private Integer errorCountWindow;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_upload_at")
    private Instant lastUploadAt;

    @Column(name = "last_manifest_at")
    private Instant lastManifestAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (startedAt == null) startedAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
        if (status == null) status = AgentInstanceStatusType.ONLINE;
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

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public AgentInstanceStatusType getStatus() {
        return status;
    }

    public void setStatus(AgentInstanceStatusType status) {
        this.status = status;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public Integer getUploadsInFlight() {
        return uploadsInFlight;
    }

    public void setUploadsInFlight(Integer uploadsInFlight) {
        this.uploadsInFlight = uploadsInFlight;
    }

    public Integer getPendingFiles() {
        return pendingFiles;
    }

    public void setPendingFiles(Integer pendingFiles) {
        this.pendingFiles = pendingFiles;
    }

    public Integer getErrorCountWindow() {
        return errorCountWindow;
    }

    public void setErrorCountWindow(Integer errorCountWindow) {
        this.errorCountWindow = errorCountWindow;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getLastUploadAt() {
        return lastUploadAt;
    }

    public void setLastUploadAt(Instant lastUploadAt) {
        this.lastUploadAt = lastUploadAt;
    }

    public Instant getLastManifestAt() {
        return lastManifestAt;
    }

    public void setLastManifestAt(Instant lastManifestAt) {
        this.lastManifestAt = lastManifestAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
