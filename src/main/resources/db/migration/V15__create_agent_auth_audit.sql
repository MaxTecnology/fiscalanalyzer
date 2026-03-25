-- V15 - Auditoria dedicada de autenticacao/autorizacao (agent/admin)

CREATE TABLE IF NOT EXISTS agent_auth_audit (
    id              BIGSERIAL PRIMARY KEY,
    auth_scope      VARCHAR(20) NOT NULL,
    route           VARCHAR(120) NOT NULL,
    tenant_id       BIGINT,
    empresa_id      BIGINT,
    api_key_id      BIGINT,
    key_prefix      VARCHAR(20),
    fingerprint     VARCHAR(64),
    result          VARCHAR(30) NOT NULL,
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_auth_audit_route_created
    ON agent_auth_audit (route, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_auth_audit_result_created
    ON agent_auth_audit (result, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_auth_audit_tenant_empresa_created
    ON agent_auth_audit (tenant_id, empresa_id, created_at DESC);
