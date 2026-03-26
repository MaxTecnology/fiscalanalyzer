-- V21 - Presenca operacional de agentes (heartbeat + status)

CREATE TABLE IF NOT EXISTS agent_instance_status (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    empresa_id          BIGINT NOT NULL,
    api_key_id          BIGINT NOT NULL,
    key_prefix          VARCHAR(20),
    agent_id            VARCHAR(120) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    agent_version       VARCHAR(60),
    hostname            VARCHAR(120),
    ip_hash             VARCHAR(64),
    uploads_in_flight   INTEGER,
    pending_files       INTEGER,
    error_count_window  INTEGER,
    last_error_code     VARCHAR(60),
    last_error_message  VARCHAR(500),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_upload_at      TIMESTAMPTZ,
    last_manifest_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_agent_instance_status_scope'
    ) THEN
        ALTER TABLE agent_instance_status
            ADD CONSTRAINT uq_agent_instance_status_scope
                UNIQUE (tenant_id, empresa_id, agent_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_agent_instance_status_empresa_tenant'
    ) THEN
        ALTER TABLE agent_instance_status
            ADD CONSTRAINT fk_agent_instance_status_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_agent_instance_status_api_key'
    ) THEN
        ALTER TABLE agent_instance_status
            ADD CONSTRAINT fk_agent_instance_status_api_key
                FOREIGN KEY (api_key_id)
                REFERENCES agent_api_key (id)
                ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_agent_instance_status_status'
    ) THEN
        ALTER TABLE agent_instance_status
            ADD CONSTRAINT ck_agent_instance_status_status
                CHECK (status IN ('ONLINE', 'DEGRADED', 'OFFLINE', 'BLOCKED', 'REVOKED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_agent_instance_status_tenant_empresa_seen
    ON agent_instance_status (tenant_id, empresa_id, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_instance_status_status_seen
    ON agent_instance_status (status, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_instance_status_api_key
    ON agent_instance_status (api_key_id);
