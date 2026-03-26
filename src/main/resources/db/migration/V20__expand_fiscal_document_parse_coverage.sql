-- V20 - Expansao de cobertura de parsing (ide/emit/dest/totais/protocolo/pagamento/cobranca/observacoes)

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS numero_nota INTEGER,
    ADD COLUMN IF NOT EXISTS serie VARCHAR(10),
    ADD COLUMN IF NOT EXISTS natureza_operacao TEXT,
    ADD COLUMN IF NOT EXISTS ambiente SMALLINT,
    ADD COLUMN IF NOT EXISTS finalidade_emissao SMALLINT,
    ADD COLUMN IF NOT EXISTS consumidor_final SMALLINT,
    ADD COLUMN IF NOT EXISTS presenca_comprador SMALLINT,
    ADD COLUMN IF NOT EXISTS emit_nome TEXT,
    ADD COLUMN IF NOT EXISTS emit_ie VARCHAR(20),
    ADD COLUMN IF NOT EXISTS emit_uf CHAR(2),
    ADD COLUMN IF NOT EXISTS dest_documento VARCHAR(14),
    ADD COLUMN IF NOT EXISTS dest_nome TEXT,
    ADD COLUMN IF NOT EXISTS dest_ie VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dest_uf CHAR(2),
    ADD COLUMN IF NOT EXISTS total_frete NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS total_desconto NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS total_outros NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS total_ipi NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS total_tributos NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS protocolo_numero VARCHAR(32),
    ADD COLUMN IF NOT EXISTS protocolo_status INTEGER,
    ADD COLUMN IF NOT EXISTS protocolo_motivo TEXT,
    ADD COLUMN IF NOT EXISTS protocolo_recebimento TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS qr_code_url TEXT,
    ADD COLUMN IF NOT EXISTS fatura_numero VARCHAR(60),
    ADD COLUMN IF NOT EXISTS fatura_valor_original NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS fatura_valor_desconto NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS fatura_valor_liquido NUMERIC(15,2);

UPDATE fiscal_document
SET dest_documento = dest_cnpj
WHERE dest_documento IS NULL
  AND dest_cnpj IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fiscal_document_protocolo_numero
    ON fiscal_document (protocolo_numero);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_numero_nota
    ON fiscal_document (numero_nota);

CREATE TABLE IF NOT EXISTS fiscal_document_payment (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,
    payment_indicator SMALLINT,
    payment_type VARCHAR(4),
    payment_amount NUMERIC(15,2),
    card_integration_type SMALLINT,
    card_cnpj VARCHAR(14),
    card_brand VARCHAR(2),
    card_authorization_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_payment_document
    ON fiscal_document_payment (document_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_payment_type
    ON fiscal_document_payment (payment_type);

CREATE TABLE IF NOT EXISTS fiscal_document_duplicate (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,
    duplicate_number VARCHAR(60),
    due_date DATE,
    amount NUMERIC(15,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fiscal_document_duplicate_document
    ON fiscal_document_duplicate (document_id);

CREATE TABLE IF NOT EXISTS fiscal_document_additional_info (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,
    info_type VARCHAR(20) NOT NULL,
    field_name VARCHAR(60),
    text_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fiscal_document_additional_info_type'
    ) THEN
        ALTER TABLE fiscal_document_additional_info
            ADD CONSTRAINT chk_fiscal_document_additional_info_type
                CHECK (info_type IN ('INF_CPL', 'OBS_CONT'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fiscal_document_additional_info_document
    ON fiscal_document_additional_info (document_id);
