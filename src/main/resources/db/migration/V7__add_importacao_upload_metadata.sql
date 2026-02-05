-- V7 - Metadados do upload na importacao

ALTER TABLE importacao
    ADD COLUMN IF NOT EXISTS arquivo_tamanho BIGINT,
    ADD COLUMN IF NOT EXISTS arquivo_content_type VARCHAR(100);
