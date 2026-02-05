-- V8 - Campos e idempotencia para import_item (extracao)

ALTER TABLE importacao
    ADD COLUMN IF NOT EXISTS erro_codigo VARCHAR(50),
    ADD COLUMN IF NOT EXISTS erro_mensagem TEXT;

ALTER TABLE import_item
    ADD COLUMN IF NOT EXISTS xml_size BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS ux_import_item_importacao_xml_path
    ON import_item (importacao_id, xml_path);
