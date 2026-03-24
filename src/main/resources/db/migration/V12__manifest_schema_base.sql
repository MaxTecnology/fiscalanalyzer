-- V12 - Base de schema para ingestao por manifesto

ALTER TABLE importacao
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'ZIP';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_importacao_source_type'
    ) THEN
        ALTER TABLE importacao
            ADD CONSTRAINT chk_importacao_source_type
                CHECK (source_type IN ('ZIP', 'MANIFEST'));
    END IF;
END $$;

ALTER TABLE import_item
    ADD COLUMN IF NOT EXISTS storage_object_key TEXT;

CREATE INDEX IF NOT EXISTS idx_import_item_storage_object_key
    ON import_item (storage_object_key);
