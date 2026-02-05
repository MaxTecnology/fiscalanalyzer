-- V3 - Itens do documento fiscal (nível correto para análise de impostos)

CREATE TABLE IF NOT EXISTS fiscal_item (
                                           id                  BIGSERIAL PRIMARY KEY,
                                           document_id         BIGINT NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,

    item_number         INTEGER NOT NULL,

    product_code        VARCHAR(60),
    product_description TEXT,

    ncm                 VARCHAR(8),
    cfop                VARCHAR(4),

    cst_icms            VARCHAR(3),
    csosn               VARCHAR(3),

    quantity            NUMERIC(15,4),
    unit_price          NUMERIC(15,4),
    total_value         NUMERIC(15,2),

    icms_base           NUMERIC(15,2),
    icms_rate           NUMERIC(5,2),
    icms_value          NUMERIC(15,2),

    pis_base            NUMERIC(15,2),
    pis_rate            NUMERIC(5,2),
    pis_value           NUMERIC(15,2),

    cofins_base         NUMERIC(15,2),
    cofins_rate         NUMERIC(5,2),
    cofins_value        NUMERIC(15,2)
    );
