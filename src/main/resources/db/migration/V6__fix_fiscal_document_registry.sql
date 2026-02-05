-- V6 - Ajustes no registry: remover coluna obsoleta e reforcar relacionamento opcional com fiscal_document

ALTER TABLE fiscal_document_registry
    DROP COLUMN IF EXISTS document_id;

ALTER TABLE fiscal_document_registry
    ADD CONSTRAINT fk_registry_fiscal_document
        FOREIGN KEY (fiscal_document_id)
        REFERENCES fiscal_document(id)
        ON DELETE SET NULL;
