-- V9 - Campos minimos para parsing em import_item

ALTER TABLE import_item
    ADD COLUMN IF NOT EXISTS model SMALLINT,
    ADD COLUMN IF NOT EXISTS access_key VARCHAR(44),
    ADD COLUMN IF NOT EXISTS issue_date DATE;
