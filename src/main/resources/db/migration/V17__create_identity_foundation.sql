-- V17 - Fundacao de identidade (tenant, empresa e usuarios locais)

CREATE TABLE IF NOT EXISTS tenant (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(150) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_tenant_status'
    ) THEN
        ALTER TABLE tenant
            ADD CONSTRAINT chk_tenant_status
                CHECK (status IN ('ATIVO', 'INATIVO'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS empresa (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    cnpj            VARCHAR(14) NOT NULL,
    razao_social    VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_empresa_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenant(id)
        ON DELETE RESTRICT
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_empresa_status'
    ) THEN
        ALTER TABLE empresa
            ADD CONSTRAINT chk_empresa_status
                CHECK (status IN ('ATIVA', 'INATIVA'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_empresa_id_tenant'
    ) THEN
        ALTER TABLE empresa
            ADD CONSTRAINT uq_empresa_id_tenant
                UNIQUE (id, tenant_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_empresa_tenant_cnpj'
    ) THEN
        ALTER TABLE empresa
            ADD CONSTRAINT uq_empresa_tenant_cnpj
                UNIQUE (tenant_id, cnpj);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_empresa_tenant_id
    ON empresa (tenant_id);

CREATE INDEX IF NOT EXISTS idx_empresa_cnpj
    ON empresa (cnpj);

CREATE TABLE IF NOT EXISTS app_user (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(150) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_app_user_status'
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT chk_app_user_status
                CHECK (status IN ('ATIVO', 'INATIVO'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email_lower
    ON app_user ((lower(email)));

CREATE TABLE IF NOT EXISTS app_role (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(30) NOT NULL,
    descricao   VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_app_role_codigo'
    ) THEN
        ALTER TABLE app_role
            ADD CONSTRAINT uq_app_role_codigo
                UNIQUE (codigo);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS app_user_role (
    app_user_id  BIGINT NOT NULL,
    app_role_id  BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (app_user_id, app_role_id),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (app_user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_app_user_role_role
        FOREIGN KEY (app_role_id)
        REFERENCES app_role(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS app_user_empresa (
    app_user_id  BIGINT NOT NULL,
    empresa_id   BIGINT NOT NULL,
    tenant_id    BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (app_user_id, empresa_id),
    CONSTRAINT fk_app_user_empresa_user
        FOREIGN KEY (app_user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_app_user_empresa_empresa
        FOREIGN KEY (empresa_id, tenant_id)
        REFERENCES empresa(id, tenant_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_app_user_empresa_tenant_empresa
    ON app_user_empresa (tenant_id, empresa_id);

INSERT INTO app_role (codigo, descricao)
VALUES
    ('ADMIN', 'Administrador do sistema'),
    ('OPERADOR', 'Operador de ingestao'),
    ('LEITOR', 'Leitura e consulta')
ON CONFLICT (codigo) DO NOTHING;
