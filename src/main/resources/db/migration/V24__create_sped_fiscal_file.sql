-- V24 - Fundação SPED Fiscal (controle de arquivos para parse/conciliação)

CREATE TABLE IF NOT EXISTS sped_fiscal_file (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    original_file_name TEXT NOT NULL,
    storage_object_key TEXT NOT NULL,
    zip_entry_name TEXT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    content_size BIGINT NULL,
    content_type VARCHAR(120) NULL,
    layout_version VARCHAR(10) NULL,
    periodo_inicio DATE NULL,
    periodo_fim DATE NULL,
    line_count_declared INTEGER NULL,
    line_count_processed INTEGER NULL,
    erro_codigo VARCHAR(80) NULL,
    erro_mensagem TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_sped_fiscal_file_source_type'
    ) THEN
        ALTER TABLE sped_fiscal_file
            ADD CONSTRAINT chk_sped_fiscal_file_source_type
                CHECK (source_type IN ('TXT', 'ZIP_ENTRY'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_sped_fiscal_file_status'
    ) THEN
        ALTER TABLE sped_fiscal_file
            ADD CONSTRAINT chk_sped_fiscal_file_status
                CHECK (status IN ('PENDENTE_PARSE', 'PROCESSANDO', 'PROCESSADO', 'FALHA'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sped_fiscal_file_scope_hash
    ON sped_fiscal_file (tenant_id, empresa_id, hash_sha256);

CREATE INDEX IF NOT EXISTS idx_sped_fiscal_file_scope_status_created
    ON sped_fiscal_file (tenant_id, empresa_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sped_fiscal_file_scope_periodo
    ON sped_fiscal_file (tenant_id, empresa_id, periodo_inicio, periodo_fim);

CREATE INDEX IF NOT EXISTS idx_sped_fiscal_file_scope_created
    ON sped_fiscal_file (tenant_id, empresa_id, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_sped_fiscal_file_empresa_tenant'
    ) THEN
        ALTER TABLE sped_fiscal_file
            ADD CONSTRAINT fk_sped_fiscal_file_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sped_fiscal_file s
        LEFT JOIN empresa e
            ON e.id = s.empresa_id
           AND e.tenant_id = s.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE sped_fiscal_file VALIDATE CONSTRAINT fk_sped_fiscal_file_empresa_tenant;
    END IF;
END $$;

