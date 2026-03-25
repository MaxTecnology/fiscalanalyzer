-- V18 - Integridade referencial tenant/empresa nas tabelas de dominio
-- Estrategia:
-- 1) adiciona FKs como NOT VALID para nao bloquear ambientes com legado
-- 2) valida automaticamente quando nao houver orfaos

CREATE INDEX IF NOT EXISTS idx_importacao_empresa_tenant
    ON importacao (empresa_id, tenant_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_importacao_empresa_tenant'
    ) THEN
        ALTER TABLE importacao
            ADD CONSTRAINT fk_importacao_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM importacao i
                 LEFT JOIN empresa e
                           ON e.id = i.empresa_id
                               AND e.tenant_id = i.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE importacao VALIDATE CONSTRAINT fk_importacao_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_fiscal_document_empresa_tenant'
    ) THEN
        ALTER TABLE fiscal_document
            ADD CONSTRAINT fk_fiscal_document_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM fiscal_document d
                 LEFT JOIN empresa e
                           ON e.id = d.empresa_id
                               AND e.tenant_id = d.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE fiscal_document VALIDATE CONSTRAINT fk_fiscal_document_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_registry_empresa_tenant'
    ) THEN
        ALTER TABLE fiscal_document_registry
            ADD CONSTRAINT fk_registry_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM fiscal_document_registry r
                 LEFT JOIN empresa e
                           ON e.id = r.empresa_id
                               AND e.tenant_id = r.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE fiscal_document_registry VALIDATE CONSTRAINT fk_registry_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_api_key_empresa_tenant'
    ) THEN
        ALTER TABLE agent_api_key
            ADD CONSTRAINT fk_agent_api_key_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM agent_api_key a
                 LEFT JOIN empresa e
                           ON e.id = a.empresa_id
                               AND e.tenant_id = a.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE agent_api_key VALIDATE CONSTRAINT fk_agent_api_key_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_upload_audit_empresa_tenant'
    ) THEN
        ALTER TABLE agent_upload_audit
            ADD CONSTRAINT fk_agent_upload_audit_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM agent_upload_audit a
                 LEFT JOIN empresa e
                           ON e.id = a.empresa_id
                               AND e.tenant_id = a.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE agent_upload_audit VALIDATE CONSTRAINT fk_agent_upload_audit_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_auth_audit_empresa_tenant'
    ) THEN
        ALTER TABLE agent_auth_audit
            ADD CONSTRAINT fk_agent_auth_audit_empresa_tenant
                FOREIGN KEY (empresa_id, tenant_id)
                REFERENCES empresa (id, tenant_id)
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM agent_auth_audit a
                 LEFT JOIN empresa e
                           ON e.id = a.empresa_id
                               AND e.tenant_id = a.tenant_id
        WHERE a.empresa_id IS NOT NULL
          AND a.tenant_id IS NOT NULL
          AND e.id IS NULL
    ) THEN
        ALTER TABLE agent_auth_audit VALIDATE CONSTRAINT fk_agent_auth_audit_empresa_tenant;
    END IF;
END $$;
