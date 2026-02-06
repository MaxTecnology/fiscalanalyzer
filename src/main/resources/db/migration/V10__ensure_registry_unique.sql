-- V10 - Garantir unicidade em fiscal_document_registry

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_registry_tenant_empresa_access'
    ) THEN
        ALTER TABLE fiscal_document_registry
            ADD CONSTRAINT uq_registry_tenant_empresa_access
                UNIQUE (tenant_id, empresa_id, access_key);
    END IF;
END $$;
