-- V22 - Suporte a eventos fiscais (ex.: cancelamento) e vinculo com documento

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS status_documento VARCHAR(20),
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_event_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancel_reason TEXT,
    ADD COLUMN IF NOT EXISTS cancel_protocol VARCHAR(32),
    ADD COLUMN IF NOT EXISTS cancel_sequence INTEGER;

UPDATE fiscal_document
SET status_documento = 'ATIVA'
WHERE status_documento IS NULL;

ALTER TABLE fiscal_document
    ALTER COLUMN status_documento SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fiscal_document_status_documento'
    ) THEN
        ALTER TABLE fiscal_document
            ADD CONSTRAINT chk_fiscal_document_status_documento
                CHECK (status_documento IN ('ATIVA', 'CANCELADA'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS fiscal_document_event (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    fiscal_document_id BIGINT NULL REFERENCES fiscal_document(id) ON DELETE SET NULL,
    access_key VARCHAR(44) NOT NULL,
    model SMALLINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(10) NOT NULL,
    event_description VARCHAR(120),
    event_sequence INTEGER,
    event_datetime TIMESTAMPTZ,
    event_registration_datetime TIMESTAMPTZ,
    event_status INTEGER,
    event_status_reason TEXT,
    event_protocol VARCHAR(32),
    reference_protocol VARCHAR(32),
    author_cnpj CHAR(14),
    xml_path TEXT NOT NULL,
    xml_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_fiscal_document_event_scope_event_id'
    ) THEN
        ALTER TABLE fiscal_document_event
            ADD CONSTRAINT uk_fiscal_document_event_scope_event_id
                UNIQUE (tenant_id, empresa_id, event_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fiscal_document_event_scope_access_key
    ON fiscal_document_event (tenant_id, empresa_id, access_key);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_event_document_id
    ON fiscal_document_event (fiscal_document_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_status_documento
    ON fiscal_document (status_documento);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_fiscal_document_event_empresa_tenant'
    ) THEN
        ALTER TABLE fiscal_document_event
            ADD CONSTRAINT fk_fiscal_document_event_empresa_tenant
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
        FROM fiscal_document_event e
        LEFT JOIN empresa emp
            ON emp.id = e.empresa_id
           AND emp.tenant_id = e.tenant_id
        WHERE emp.id IS NULL
    ) THEN
        ALTER TABLE fiscal_document_event VALIDATE CONSTRAINT fk_fiscal_document_event_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_fiscal_document_cancel_event'
    ) THEN
        ALTER TABLE fiscal_document
            ADD CONSTRAINT fk_fiscal_document_cancel_event
                FOREIGN KEY (cancel_event_id)
                REFERENCES fiscal_document_event (id)
                ON DELETE SET NULL;
    END IF;
END $$;

