# FiscalAnalyzer – Database Rules

## Banco de dados
- PostgreSQL 16+
- Migrações gerenciadas exclusivamente por Flyway
- Nenhuma migration aplicada pode ser editada após execução

## Estratégia
- Flyway é a fonte da verdade do schema
- Hibernate/JPA roda com `ddl-auto=validate`
- Divergências devem ser resolvidas com novas migrations

## Convenções de tipos
- access_key: VARCHAR(44)
- CNPJ: CHAR(14)
- source_type (importacao): VARCHAR(20) (`ZIP` ou `MANIFEST`)
- Datas fiscais:
    - issue_date: DATE (particionamento)
    - issue_datetime: TIMESTAMPTZ

## Particionamento
- fiscal_document é particionada por RANGE(issue_date)
- Constraints UNIQUE globais não são usadas diretamente em tabelas particionadas

## Idempotência
- A unicidade de documentos fiscais é garantida via:
    - fiscal_document_registry
- Essa tabela é usada para:
    - garantir idempotência de importações
    - validar SPED Contribuições no futuro

## Regra de evolução
- Qualquer nova coluna/tabela deve:
    - ser criada via migration
    - refletir corretamente nos Entities JPA

## Manifesto (base de schema)
- `importacao.source_type` identifica origem do lote:
    - `ZIP` (fluxo legado de upload)
    - `MANIFEST` (fluxo de alto volume)
- `import_item.storage_object_key` armazena a chave do XML no storage para parse direto
  (quando o fluxo não depende de ZIP).

## Operação em alto volume
- `import_item` deve ser escrito e atualizado em lote sempre que possível.
- Evitar scans de status por item em loops críticos; preferir agregação/counters.
- Se houver ingestão por manifesto, o manifesto é a fonte para criação de `import_item`.
- Para reduzir custo de storage, persistir no banco os metadados necessários para reprocesso
  sem depender de `LIST` massivo no bucket.

## Cobertura fiscal expandida (V20)
- `fiscal_document` recebeu colunas adicionais para grupos de alto valor analítico:
    - ide (`numero_nota`, `serie`, `natureza_operacao`, `ambiente`, `finalidade_emissao`, `consumidor_final`, `presenca_comprador`)
    - emit/dest (`emit_nome`, `emit_ie`, `emit_uf`, `dest_documento`, `dest_nome`, `dest_ie`, `dest_uf`)
    - totais complementares (`total_frete`, `total_desconto`, `total_outros`, `total_ipi`, `total_tributos`)
    - protocolo/suplementar (`protocolo_numero`, `protocolo_status`, `protocolo_motivo`, `protocolo_recebimento`, `qr_code_url`)
    - cobrança/fatura (`fatura_numero`, `fatura_valor_original`, `fatura_valor_desconto`, `fatura_valor_liquido`)
- Novas tabelas:
    - `fiscal_document_payment` (pagamentos `detPag/card`)
    - `fiscal_document_duplicate` (duplicatas `cobr/dup`)
    - `fiscal_document_additional_info` (informações adicionais `infCpl` e `obsCont`)

## Viewer de documentos (V23)
- `fiscal_document` recebeu colunas adicionais para consultas do front:
    - `emit_municipio`
    - `dest_municipio`
    - `total_iss`
- `fiscal_item` recebeu:
    - `unidade`
- Índices de leitura adicionados para filtros de listagem:
    - `(tenant_id, empresa_id, issue_date desc)`
    - `(tenant_id, empresa_id, model, issue_date desc)`
    - `(tenant_id, empresa_id, operation_type, issue_date desc)`
    - `(tenant_id, empresa_id, emit_cnpj, issue_date desc)`
    - `(tenant_id, empresa_id, dest_cnpj, issue_date desc)`
    - `(tenant_id, empresa_id, importacao_id)`

## Cobertura fiscal operacional (Fase 3)
- Endpoints de cobertura/backfill usam o schema atual da `V20` (sem nova migration).
- Backfill relê XML por:
    - `import_item.storage_object_key` (manifesto)
    - ou `importacao.arquivo_path` + `import_item.xml_path` (ZIP legado)
- Reprocesso mantém idempotência por documento existente (`tenant_id`, `empresa_id`, `access_key`).

## Eventos fiscais e cancelamento (V22)
- Nova tabela `fiscal_document_event` para persistir XMLs de evento (`procEventoNFe` / `evento`).
- Campos principais:
    - escopo: `tenant_id`, `empresa_id`
    - vínculo: `fiscal_document_id` (nullable, para evento chegar antes do documento)
    - chave da nota: `access_key`
    - identificação do evento: `event_id`, `event_type`, `event_sequence`
    - retorno Sefaz: `event_status`, `event_status_reason`, `event_protocol`
    - metadados: `event_datetime`, `event_registration_datetime`, `xml_path`, `xml_hash`
- Idempotência de evento:
    - `UNIQUE (tenant_id, empresa_id, event_id)`
- `fiscal_document` recebeu colunas de estado:
    - `status_documento` (`ATIVA` ou `CANCELADA`)
    - `cancelled_at`, `cancel_event_id`, `cancel_reason`, `cancel_protocol`, `cancel_sequence`
- Regra de aplicação:
    - evento `110111` (cancelamento) com retorno aceito (`cStat` 135/136/155) marca o documento como `CANCELADA`.
    - documento não é apagado; apenas estado e metadados de cancelamento são atualizados.

## Identidade e governança (decisão 2026-03-25)
- `tenant` e `empresa` são entidades mestre internas do FiscalAnalyzer.
- `tenant_id` e `empresa_id` não podem existir “soltos” nas tabelas de domínio.
- Novas evoluções devem priorizar FK composta `(empresa_id, tenant_id) -> empresa(id, tenant_id)`.
- Não criar ApiKey/importação/documento para tenant/empresa inexistente ou inativo.
- Plano completo e checklist:
    - `TENANT_EMPRESA_USUARIO_DECISION.md`
    - `IDENTITY_FOUNDATION_CHECKLIST.md`

## Fundação de identidade implementada (V17)
- Tabelas criadas:
    - `tenant`
    - `empresa`
    - `app_user`
    - `app_role`
    - `app_user_role`
    - `app_user_empresa`
- Roles base inseridas via migration:
    - `ADMIN`
    - `OPERADOR`
    - `LEITOR`
- Unicidade adicionada:
    - `empresa (tenant_id, cnpj)`
    - `app_role (codigo)`
    - `app_user lower(email)` (case-insensitive)

## Regra de validação de domínio (backend)
- Rotas críticas validam tenant/empresa antes de processar:
    - upload ZIP
    - manifesto
    - leitura de documento por access key
    - gestão e autenticação de Agent ApiKey
- Semântica de erro:
    - inexistente: `422 UNPROCESSABLE_ENTITY`
    - inativo: `403 AUTH_FORBIDDEN`

## Integridade referencial em domínio existente (V18)
- FKs compostas adicionadas em:
    - `importacao`
    - `fiscal_document`
    - `fiscal_document_registry`
    - `agent_api_key`
    - `agent_upload_audit`
    - `agent_auth_audit`
- Referência usada: `(empresa_id, tenant_id) -> empresa(id, tenant_id)`.
- Migração usa `NOT VALID` para não quebrar ambientes legados; quando não há órfãos a validação é feita automaticamente.

## Presença operacional do agent (V21)
- Nova tabela `agent_instance_status` para monitoramento por instância:
    - chave de escopo: `UNIQUE (tenant_id, empresa_id, agent_id)`
    - vínculo de segurança: `api_key_id` + `key_prefix`
    - sinais operacionais: `status`, `last_seen_at`, `started_at`
    - telemetria: `uploads_in_flight`, `pending_files`, `error_count_window`
- Status suportados:
    - `ONLINE`
    - `DEGRADED`
    - `OFFLINE`
    - `BLOCKED`
    - `REVOKED`

## Fundação SPED Fiscal (V24)
- Nova tabela `sped_fiscal_file` para controle de ingestão de arquivo SPED:
    - escopo: `tenant_id`, `empresa_id`
    - origem: `source_type` (`TXT` ou `ZIP_ENTRY`)
    - status: `PENDENTE_PARSE`, `PROCESSANDO`, `PROCESSADO`, `FALHA`
    - arquivo: `original_file_name`, `storage_object_key`, `zip_entry_name`
    - integridade: `hash_sha256`, `content_size`, `content_type`
    - metadados de parse: `layout_version`, `periodo_inicio`, `periodo_fim`, contagem de linhas
    - erro operacional: `erro_codigo`, `erro_mensagem`
- Idempotência:
    - `UNIQUE (tenant_id, empresa_id, hash_sha256)`
- Índices operacionais:
    - `(tenant_id, empresa_id, status, created_at desc)`
    - `(tenant_id, empresa_id, periodo_inicio, periodo_fim)`
    - `(tenant_id, empresa_id, created_at desc)`
- Integridade referencial:
    - FK composta `(empresa_id, tenant_id) -> empresa(id, tenant_id)` com `NOT VALID` + validação condicional.

## Documentos SPED C100 (V25)
- Nova tabela `sped_fiscal_c100` para persistir documentos fiscais do SPED por arquivo:
    - vínculo: `file_id -> sped_fiscal_file.id` (`ON DELETE CASCADE`)
    - escopo: `tenant_id`, `empresa_id`
    - origem de linha: `linha_origem`
    - chaves de documento: `cod_mod`, `serie`, `num_doc`, `chv_nfe`, `dt_doc`, `dt_es`
    - valores fiscais: `vl_doc`, `vl_desc`, `vl_merc`, `vl_bc_icms`, `vl_icms`, `vl_bc_icms_st`, `vl_icms_st`, `vl_ipi`, `vl_pis`, `vl_cofins`, `vl_pis_st`, `vl_cofins_st`
- Idempotência intra-arquivo:
    - `UNIQUE (file_id, linha_origem)`
- Índices operacionais:
    - `(file_id)`
    - `(tenant_id, empresa_id, chv_nfe)`
    - `(tenant_id, empresa_id, dt_doc)`
- Integridade referencial:
    - FK composta `(empresa_id, tenant_id) -> empresa(id, tenant_id)` com `NOT VALID` + validação condicional.

## Blocos SPED complementares (V26)
- Novas tabelas:
    - `sped_fiscal_participant` (registro `0150`);
    - `sped_fiscal_c170` (itens `C170`);
    - `sped_fiscal_c190` (analítico ICMS `C190`);
    - `sped_fiscal_e110` (apuração `E110`);
    - `sped_fiscal_e111` (ajustes `E111`).
- Relações:
    - `sped_fiscal_participant.file_id -> sped_fiscal_file.id`
    - `sped_fiscal_c170.c100_id -> sped_fiscal_c100.id`
    - `sped_fiscal_c190.c100_id -> sped_fiscal_c100.id`
    - `sped_fiscal_e110.file_id -> sped_fiscal_file.id`
    - `sped_fiscal_e111.e110_id -> sped_fiscal_e110.id`
- Idempotência por linha:
    - `UNIQUE (file_id, linha_origem)` para `0150` e `E110`;
    - `UNIQUE (c100_id, linha_origem)` para `C170` e `C190`;
    - `UNIQUE (e110_id, linha_origem)` para `E111`.
- Parse/reprocesso:
    - ao reprocessar arquivo SPED, os registros de blocos persistidos para o `file_id` são recriados em transação única.
