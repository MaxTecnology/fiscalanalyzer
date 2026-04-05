-- V25 - Persistência de documentos SPED (registro C100) por arquivo

CREATE TABLE IF NOT EXISTS sped_fiscal_c100 (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    linha_origem INTEGER NOT NULL,
    ind_oper VARCHAR(1) NULL,
    ind_emit VARCHAR(1) NULL,
    cod_part VARCHAR(60) NULL,
    cod_mod VARCHAR(2) NULL,
    cod_sit VARCHAR(2) NULL,
    serie VARCHAR(10) NULL,
    num_doc VARCHAR(20) NULL,
    chv_nfe VARCHAR(44) NULL,
    dt_doc DATE NULL,
    dt_es DATE NULL,
    vl_doc NUMERIC(15,2) NULL,
    vl_desc NUMERIC(15,2) NULL,
    vl_merc NUMERIC(15,2) NULL,
    vl_bc_icms NUMERIC(15,2) NULL,
    vl_icms NUMERIC(15,2) NULL,
    vl_bc_icms_st NUMERIC(15,2) NULL,
    vl_icms_st NUMERIC(15,2) NULL,
    vl_ipi NUMERIC(15,2) NULL,
    vl_pis NUMERIC(15,2) NULL,
    vl_cofins NUMERIC(15,2) NULL,
    vl_pis_st NUMERIC(15,2) NULL,
    vl_cofins_st NUMERIC(15,2) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_sped_fiscal_c100_file'
    ) THEN
        ALTER TABLE sped_fiscal_c100
            ADD CONSTRAINT fk_sped_fiscal_c100_file
                FOREIGN KEY (file_id)
                REFERENCES sped_fiscal_file (id)
                ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_sped_fiscal_c100_empresa_tenant'
    ) THEN
        ALTER TABLE sped_fiscal_c100
            ADD CONSTRAINT fk_sped_fiscal_c100_empresa_tenant
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
        FROM sped_fiscal_c100 s
        LEFT JOIN empresa e
            ON e.id = s.empresa_id
           AND e.tenant_id = s.tenant_id
        WHERE e.id IS NULL
    ) THEN
        ALTER TABLE sped_fiscal_c100 VALIDATE CONSTRAINT fk_sped_fiscal_c100_empresa_tenant;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_sped_fiscal_c100_file_line'
    ) THEN
        ALTER TABLE sped_fiscal_c100
            ADD CONSTRAINT uk_sped_fiscal_c100_file_line
                UNIQUE (file_id, linha_origem);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sped_fiscal_c100_file
    ON sped_fiscal_c100 (file_id);

CREATE INDEX IF NOT EXISTS idx_sped_fiscal_c100_scope_chv
    ON sped_fiscal_c100 (tenant_id, empresa_id, chv_nfe);

CREATE INDEX IF NOT EXISTS idx_sped_fiscal_c100_scope_dt_doc
    ON sped_fiscal_c100 (tenant_id, empresa_id, dt_doc);
