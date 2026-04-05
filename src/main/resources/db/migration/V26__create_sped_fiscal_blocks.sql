-- V26 - Persistencia de blocos SPED complementares (0150, C170, C190, E110, E111)

CREATE TABLE IF NOT EXISTS sped_fiscal_participant (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    linha_origem INTEGER NOT NULL,
    cod_part VARCHAR(60) NOT NULL,
    nome TEXT NULL,
    cod_pais VARCHAR(5) NULL,
    cnpj VARCHAR(14) NULL,
    cpf VARCHAR(11) NULL,
    ie VARCHAR(20) NULL,
    cod_mun VARCHAR(7) NULL,
    suframa VARCHAR(20) NULL,
    endereco TEXT NULL,
    numero VARCHAR(60) NULL,
    complemento TEXT NULL,
    bairro TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sped_fiscal_c170 (
    id BIGSERIAL PRIMARY KEY,
    c100_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    linha_origem INTEGER NOT NULL,
    num_item INTEGER NULL,
    cod_item VARCHAR(60) NULL,
    descr_compl TEXT NULL,
    qtd NUMERIC(15,4) NULL,
    unid VARCHAR(10) NULL,
    vl_item NUMERIC(15,2) NULL,
    vl_desc NUMERIC(15,2) NULL,
    cst_icms VARCHAR(3) NULL,
    cfop VARCHAR(4) NULL,
    vl_bc_icms NUMERIC(15,2) NULL,
    aliq_icms NUMERIC(7,4) NULL,
    vl_icms NUMERIC(15,2) NULL,
    cst_ipi VARCHAR(3) NULL,
    vl_bc_ipi NUMERIC(15,2) NULL,
    aliq_ipi NUMERIC(7,4) NULL,
    vl_ipi NUMERIC(15,2) NULL,
    cst_pis VARCHAR(3) NULL,
    vl_bc_pis NUMERIC(15,2) NULL,
    aliq_pis NUMERIC(7,4) NULL,
    vl_pis NUMERIC(15,2) NULL,
    cst_cofins VARCHAR(3) NULL,
    vl_bc_cofins NUMERIC(15,2) NULL,
    aliq_cofins NUMERIC(7,4) NULL,
    vl_cofins NUMERIC(15,2) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sped_fiscal_c190 (
    id BIGSERIAL PRIMARY KEY,
    c100_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    linha_origem INTEGER NOT NULL,
    cst_icms VARCHAR(3) NULL,
    cfop VARCHAR(4) NULL,
    aliq_icms NUMERIC(7,4) NULL,
    vl_opr NUMERIC(15,2) NULL,
    vl_bc_icms NUMERIC(15,2) NULL,
    vl_icms NUMERIC(15,2) NULL,
    vl_bc_icms_st NUMERIC(15,2) NULL,
    vl_icms_st NUMERIC(15,2) NULL,
    vl_red_bc NUMERIC(15,2) NULL,
    vl_ipi NUMERIC(15,2) NULL,
    cod_obs VARCHAR(10) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sped_fiscal_e110 (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    linha_origem INTEGER NOT NULL,
    vl_tot_debitos NUMERIC(15,2) NULL,
    vl_aj_debitos NUMERIC(15,2) NULL,
    vl_tot_aj_debitos NUMERIC(15,2) NULL,
    vl_estornos_cred NUMERIC(15,2) NULL,
    vl_tot_creditos NUMERIC(15,2) NULL,
    vl_aj_creditos NUMERIC(15,2) NULL,
    vl_tot_aj_creditos NUMERIC(15,2) NULL,
    vl_estornos_deb NUMERIC(15,2) NULL,
    vl_sld_credor_anterior NUMERIC(15,2) NULL,
    vl_sld_apurado NUMERIC(15,2) NULL,
    vl_tot_ded NUMERIC(15,2) NULL,
    vl_icms_recolher NUMERIC(15,2) NULL,
    vl_sld_credor_transportar NUMERIC(15,2) NULL,
    deb_esp NUMERIC(15,2) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sped_fiscal_e111 (
    id BIGSERIAL PRIMARY KEY,
    e110_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    linha_origem INTEGER NOT NULL,
    cod_aj_apur VARCHAR(20) NULL,
    descr_compl_aj TEXT NULL,
    vl_aj_apur NUMERIC(15,2) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sped_participant_file') THEN
        ALTER TABLE sped_fiscal_participant
            ADD CONSTRAINT fk_sped_participant_file
                FOREIGN KEY (file_id) REFERENCES sped_fiscal_file(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sped_c170_c100') THEN
        ALTER TABLE sped_fiscal_c170
            ADD CONSTRAINT fk_sped_c170_c100
                FOREIGN KEY (c100_id) REFERENCES sped_fiscal_c100(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sped_c190_c100') THEN
        ALTER TABLE sped_fiscal_c190
            ADD CONSTRAINT fk_sped_c190_c100
                FOREIGN KEY (c100_id) REFERENCES sped_fiscal_c100(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sped_e110_file') THEN
        ALTER TABLE sped_fiscal_e110
            ADD CONSTRAINT fk_sped_e110_file
                FOREIGN KEY (file_id) REFERENCES sped_fiscal_file(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sped_e111_e110') THEN
        ALTER TABLE sped_fiscal_e111
            ADD CONSTRAINT fk_sped_e111_e110
                FOREIGN KEY (e110_id) REFERENCES sped_fiscal_e110(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_participant_file_line') THEN
        ALTER TABLE sped_fiscal_participant
            ADD CONSTRAINT uk_sped_participant_file_line UNIQUE (file_id, linha_origem);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_participant_file_cod_part') THEN
        ALTER TABLE sped_fiscal_participant
            ADD CONSTRAINT uk_sped_participant_file_cod_part UNIQUE (file_id, cod_part);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_c170_c100_line') THEN
        ALTER TABLE sped_fiscal_c170
            ADD CONSTRAINT uk_sped_c170_c100_line UNIQUE (c100_id, linha_origem);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_c170_c100_item') THEN
        ALTER TABLE sped_fiscal_c170
            ADD CONSTRAINT uk_sped_c170_c100_item UNIQUE (c100_id, num_item);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_c190_c100_line') THEN
        ALTER TABLE sped_fiscal_c190
            ADD CONSTRAINT uk_sped_c190_c100_line UNIQUE (c100_id, linha_origem);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_e110_file_line') THEN
        ALTER TABLE sped_fiscal_e110
            ADD CONSTRAINT uk_sped_e110_file_line UNIQUE (file_id, linha_origem);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sped_e111_e110_line') THEN
        ALTER TABLE sped_fiscal_e111
            ADD CONSTRAINT uk_sped_e111_e110_line UNIQUE (e110_id, linha_origem);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sped_participant_file ON sped_fiscal_participant(file_id);
CREATE INDEX IF NOT EXISTS idx_sped_participant_scope_cnpj ON sped_fiscal_participant(tenant_id, empresa_id, cnpj);
CREATE INDEX IF NOT EXISTS idx_sped_c170_c100 ON sped_fiscal_c170(c100_id);
CREATE INDEX IF NOT EXISTS idx_sped_c170_scope_cfop ON sped_fiscal_c170(tenant_id, empresa_id, cfop);
CREATE INDEX IF NOT EXISTS idx_sped_c190_c100 ON sped_fiscal_c190(c100_id);
CREATE INDEX IF NOT EXISTS idx_sped_c190_scope_cfop ON sped_fiscal_c190(tenant_id, empresa_id, cfop);
CREATE INDEX IF NOT EXISTS idx_sped_e110_file ON sped_fiscal_e110(file_id);
CREATE INDEX IF NOT EXISTS idx_sped_e111_e110 ON sped_fiscal_e111(e110_id);
