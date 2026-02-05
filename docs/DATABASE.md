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
