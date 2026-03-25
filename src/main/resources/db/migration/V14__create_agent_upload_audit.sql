-- V14 - Auditoria minima para endpoint de upload-url do agente

CREATE TABLE IF NOT EXISTS agent_upload_audit (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    empresa_id      BIGINT NOT NULL,
    api_key_id      BIGINT,
    key_prefix      VARCHAR(20),
    endpoint        VARCHAR(50) NOT NULL,
    object_key      TEXT,
    sha256          VARCHAR(64),
    status          VARCHAR(30) NOT NULL,
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_upload_audit_tenant_empresa_created
    ON agent_upload_audit (tenant_id, empresa_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_upload_audit_status_created
    ON agent_upload_audit (status, created_at DESC);
