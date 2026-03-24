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
