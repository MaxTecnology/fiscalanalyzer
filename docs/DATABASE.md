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

## Cobertura fiscal operacional (Fase 3)
- Endpoints de cobertura/backfill usam o schema atual da `V20` (sem nova migration).
- Backfill relê XML por:
    - `import_item.storage_object_key` (manifesto)
    - ou `importacao.arquivo_path` + `import_item.xml_path` (ZIP legado)
- Reprocesso mantém idempotência por documento existente (`tenant_id`, `empresa_id`, `access_key`).

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
