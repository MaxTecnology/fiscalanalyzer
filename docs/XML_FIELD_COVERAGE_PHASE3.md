# FiscalAnalyzer — Cobertura de Campos XML (Fase 3)

Data de referência: **2026-03-26**

Escopo desta fase:

- consolidar observabilidade de cobertura fiscal já persistida na fase 2
- habilitar backfill controlado para reprocessar XMLs já importados
- manter parser streaming e `ddl-auto=validate`

## 1. Endpoints adicionados

- `GET /documents/coverage?tenantId=&empresaId=`
  - retorna visão agregada de cobertura dos documentos já persistidos
- `POST /documents/coverage/backfill?tenantId=&empresaId=&limit=`
  - reprocessa itens elegíveis (`PARSEADO`, `DUPLICADO`) e atualiza campos de cobertura em lote

Regras:

- `GET /documents/coverage` requer escopo de leitura (`assertCanRead`)
- `POST /documents/coverage/backfill` requer escopo de escrita (`assertCanWrite`)
- `limit` é protegido no backend (clamp entre `1` e `5000`)

## 2. Métricas de backfill

Counters registrados:

- `document.coverage.backfill.processed`
- `document.coverage.backfill.updated`
- `document.coverage.backfill.skipped`
- `document.coverage.backfill.failed`

## 3. Estratégia de origem para reprocesso

O backfill detecta automaticamente a origem do XML:

- manifesto (XML direto): usa `import_item.storage_object_key`
- upload ZIP legado: usa `importacao.arquivo_path` + `import_item.xml_path`

Sem estratégia de origem válida:

- item é contabilizado como falha no relatório do backfill
- processamento segue para os próximos itens (best effort)

## 4. Cobertura validada por testes

- `DocumentCoverageServiceTest`
  - agregação de cobertura
  - backfill de XML direto (manifesto), incluindo atualização de documento e tabelas filhas
- `DocumentControllerTest`
  - autorização + contrato HTTP de:
    - `GET /documents/coverage`
    - `POST /documents/coverage/backfill`

## 5. Observações operacionais

- esta fase não introduziu migration nova; usa o schema expandido da `V20`
- o backfill é idempotente por `access_key` + `tenant/empresa` (via documento já existente)
- recomendado executar backfill por lotes pequenos em homologação antes de produção
