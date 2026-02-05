-- V4 - Índices para escala (sem particionamento no MVP)

CREATE INDEX IF NOT EXISTS idx_fiscal_document_empresa_data
    ON fiscal_document (empresa_id, issue_date);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_empresa_model_data
    ON fiscal_document (empresa_id, model, issue_date);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_emitente
    ON fiscal_document (empresa_id, emit_cnpj, issue_date);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_destinatario
    ON fiscal_document (empresa_id, dest_cnpj, issue_date);


CREATE INDEX IF NOT EXISTS idx_fiscal_item_document
    ON fiscal_item (document_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_item_cfop
    ON fiscal_item (cfop);

CREATE INDEX IF NOT EXISTS idx_fiscal_item_ncm
    ON fiscal_item (ncm);

CREATE INDEX IF NOT EXISTS idx_fiscal_item_cst
    ON fiscal_item (cst_icms, csosn);
