-- V5 - registry para idempotência global por (tenant_id, empresa_id, access_key)
-- Útil porque fiscal_document é particionada por issue_date e não suporta UNIQUE global sem incluir a partição.

CREATE TABLE IF NOT EXISTS fiscal_document_registry (
                                                        id          BIGSERIAL PRIMARY KEY,
                                                        tenant_id   BIGINT NOT NULL,
                                                        empresa_id  BIGINT NOT NULL,
                                                        fiscal_document_id BIGINT,
                                                        access_key  VARCHAR(44) NOT NULL,
    document_id BIGINT, -- opcional: pode apontar para fiscal_document.id após persist
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_registry_tenant_empresa_access UNIQUE (tenant_id, empresa_id, access_key)
    );

CREATE INDEX IF NOT EXISTS idx_registry_empresa_access
    ON fiscal_document_registry (empresa_id, access_key);

CREATE INDEX IF NOT EXISTS idx_registry_fiscal_document_id
    ON fiscal_document_registry (fiscal_document_id);
