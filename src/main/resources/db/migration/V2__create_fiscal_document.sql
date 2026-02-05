-- V2 - Documento fiscal (NF-e/NFC-e 55/65) - versão sem particionamento (MVP)

CREATE TABLE IF NOT EXISTS fiscal_document (
                                               id                  BIGSERIAL PRIMARY KEY,
                                               tenant_id           BIGINT NOT NULL,
                                               empresa_id          BIGINT NOT NULL,

                                               model               SMALLINT NOT NULL, -- 55 ou 65
                                               access_key          VARCHAR(44) NOT NULL,

    issue_date          DATE NOT NULL,
    issue_datetime      TIMESTAMPTZ,

    operation_type      VARCHAR(1) NOT NULL, -- E=entrada, S=saída

    emit_cnpj           VARCHAR(14) NOT NULL,
    dest_cnpj           VARCHAR(14),

    total_products      NUMERIC(15,2),
    total_amount        NUMERIC(15,2) NOT NULL,

    total_icms          NUMERIC(15,2),
    total_pis           NUMERIC(15,2),
    total_cofins        NUMERIC(15,2),

    importacao_id       BIGINT REFERENCES importacao(id),

    xml_path            TEXT NOT NULL,
    xml_hash            VARCHAR(64) NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_fiscal_document_model
    CHECK (model IN (55, 65)),

    CONSTRAINT chk_fiscal_document_operation_type
    CHECK (operation_type IN ('E', 'S'))
    );

-- Idempotência global
CREATE UNIQUE INDEX IF NOT EXISTS ux_fiscal_document_empresa_chave
    ON fiscal_document (empresa_id, access_key);
