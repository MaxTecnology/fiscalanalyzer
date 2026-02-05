-- V1 - Controle de importação (lote ZIP e itens XML)

CREATE TABLE IF NOT EXISTS importacao (
                                          id                  BIGSERIAL PRIMARY KEY,
                                          tenant_id           BIGINT NOT NULL,
                                          empresa_id          BIGINT NOT NULL,

                                          status              VARCHAR(30) NOT NULL, -- RECEBIDO, PROCESSANDO, CONCLUIDO, ERRO
    arquivo_nome        VARCHAR(255) NOT NULL,
    arquivo_path        TEXT NOT NULL,
    arquivo_hash        VARCHAR(64) NOT NULL,

    total_encontrado    INTEGER NOT NULL DEFAULT 0,
    total_processado    INTEGER NOT NULL DEFAULT 0,
    total_erros         INTEGER NOT NULL DEFAULT 0,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ
    );

CREATE INDEX IF NOT EXISTS idx_importacao_empresa_status
    ON importacao (empresa_id, status);


CREATE TABLE IF NOT EXISTS import_item (
                                           id                  BIGSERIAL PRIMARY KEY,
                                           importacao_id       BIGINT NOT NULL REFERENCES importacao(id) ON DELETE CASCADE,

    xml_path            TEXT NOT NULL,
    xml_hash            VARCHAR(64),

    status              VARCHAR(30) NOT NULL, -- PENDENTE, PROCESSANDO, CONCLUIDO, ERRO
    erro_codigo         VARCHAR(50),
    erro_mensagem       TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_import_item_importacao_status
    ON import_item (importacao_id, status);
