-- V23 - Campos e indices para viewer de documentos (listagem detalhada)

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS emit_municipio TEXT,
    ADD COLUMN IF NOT EXISTS dest_municipio TEXT,
    ADD COLUMN IF NOT EXISTS total_iss NUMERIC(15,2);

ALTER TABLE fiscal_item
    ADD COLUMN IF NOT EXISTS unidade VARCHAR(10);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_issue_date
    ON fiscal_document (tenant_id, empresa_id, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_model_issue_date
    ON fiscal_document (tenant_id, empresa_id, model, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_operation_issue_date
    ON fiscal_document (tenant_id, empresa_id, operation_type, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_emit_issue_date
    ON fiscal_document (tenant_id, empresa_id, emit_cnpj, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_dest_issue_date
    ON fiscal_document (tenant_id, empresa_id, dest_cnpj, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_tenant_empresa_importacao
    ON fiscal_document (tenant_id, empresa_id, importacao_id);
