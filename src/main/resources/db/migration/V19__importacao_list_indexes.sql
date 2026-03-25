-- V19 - Índices para listagem paginada de importações por tenant/empresa/status

CREATE INDEX IF NOT EXISTS idx_importacao_tenant_empresa_created_at
    ON importacao (tenant_id, empresa_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_importacao_tenant_empresa_status_created_at
    ON importacao (tenant_id, empresa_id, status, created_at DESC);
