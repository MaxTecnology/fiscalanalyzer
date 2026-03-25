-- V16 - Índices para retenção/limpeza incremental de auditoria de segurança

CREATE INDEX IF NOT EXISTS idx_agent_auth_audit_created_at
    ON agent_auth_audit (created_at);

CREATE INDEX IF NOT EXISTS idx_agent_upload_audit_created_at
    ON agent_upload_audit (created_at);
