-- V13 - Chaves de autenticacao para agente

CREATE TABLE IF NOT EXISTS agent_api_key (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    empresa_id      BIGINT NOT NULL,
    key_hash        VARCHAR(64) NOT NULL,
    key_prefix      VARCHAR(20) NOT NULL,
    descricao       VARCHAR(255),
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_uso_at   TIMESTAMPTZ,
    criado_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    revogado_at     TIMESTAMPTZ,
    criado_por      VARCHAR(100)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_agent_api_key_hash'
    ) THEN
        ALTER TABLE agent_api_key
            ADD CONSTRAINT uq_agent_api_key_hash UNIQUE (key_hash);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_agent_api_key_tenant_empresa_ativo
    ON agent_api_key (tenant_id, empresa_id, ativo);

CREATE INDEX IF NOT EXISTS idx_agent_api_key_prefix
    ON agent_api_key (key_prefix);
