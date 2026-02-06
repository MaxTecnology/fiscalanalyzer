-- V11 - Read model: timestamps e indices

ALTER TABLE importacao
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE import_item
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_import_item_importacao_status
    ON import_item (importacao_id, status);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_access
    ON fiscal_document (tenant_id, empresa_id, access_key);
