# Manual Test — Manifest Pipeline (Upload direto XML -> Parse -> Consulta)

Status: guia complementar (fluxo específico de manifesto).  
Para validação completa de homologação, usar `SMOKE_TEST_HOMOLOG_IDENTITY_PIPELINE.md`.

Este roteiro valida o fluxo de alto volume com manifesto:
1. XML já no storage (S3/Backblaze)
2. `POST /imports/manifest`
3. parse assíncrono
4. consultas de acompanhamento (`/imports` e `/documents`)

## Pré-requisitos
- Infra local ativa (`postgres`, `rabbitmq`)
- API rodando com perfil `local`
- Bucket S3-compatível existente (ex.: Backblaze B2)

Variáveis de ambiente esperadas pelo backend:

```bash
export STORAGE_S3_ENDPOINT="https://s3.<region>.backblazeb2.com"
export STORAGE_S3_ACCESS_KEY="<key_id>"
export STORAGE_S3_SECRET_KEY="<application_key>"
export STORAGE_S3_BUCKET="<bucket>"
export STORAGE_S3_REGION="us-east-1"
export STORAGE_S3_PATH_STYLE_ACCESS="false"
```

Opção prática com `.env`:
- copie `.env.example` para `.env.local`
- preencha os valores
- rode `./scripts/run-local.sh`

## 1) Preparar dados de teste
Use um XML válido (ex.: `docs/nfe.xml`).

```bash
sha256sum docs/nfe.xml
```

Guarde o hash (64 hex) para usar no manifesto.

## 2) Subir XML no storage S3 (sem ZIP)
Defina uma chave determinística no bucket:
- `objectKey`: `99/99/nfe-001.xml`

Suba o arquivo para o bucket configurado no backend (`STORAGE_S3_BUCKET`).

## 3) Enviar manifesto
```bash
curl -i -X POST "http://localhost:8080/imports/manifest" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 99,
    "empresaId": 99,
    "entries": [
      {
        "objectKey": "99/99/nfe-001.xml",
        "sha256": "SUBSTITUIR_PELO_SHA256",
        "sizeBytes": 0,
        "originalFileName": "nfe.xml"
      }
    ]
  }'
```

Esperado:
- HTTP `202 Accepted`
- body com `importacaoId`, `status=RECEBIDO`, `totalItems`

## 4) Acompanhar importação
Substitua `IMPORT_ID` pelo retorno do passo anterior.

```bash
curl -s "http://localhost:8080/imports/IMPORT_ID" | jq
curl -s "http://localhost:8080/imports/IMPORT_ID/items?page=0&size=50" | jq
```

Esperado após alguns segundos:
- `import_item.status` em `PARSEADO` ou `DUPLICADO`
- `accessKey`, `model`, `issueDate` preenchidos
- `importacao.status` finalizada (`CONCLUIDO`) quando não houver itens pendentes

## 5) Consultar documento por chave de acesso
Pegue a `accessKey` no endpoint de itens e consulte:

```bash
curl -s "http://localhost:8080/documents/ACCESS_KEY?tenantId=99&empresaId=99" | jq
```

Esperado:
- documento fiscal mínimo retornado
- campos como `model`, `issueDate`, `operationType`, `emitCnpj`, `totalAmount`

## 6) Verificar filas e DLQ
```bash
docker exec -it fiscalanalyzer-rabbitmq rabbitmqadmin -u fiscalanalyzer -p fiscalanalyzer list queues name messages
```

Esperado:
- `import.parse` e `import.extract` sem acúmulo
- DLQs sem crescimento inesperado em fluxo válido

## 7) Verificação rápida no banco
```sql
SELECT id, status, source_type, total_encontrado, total_processado, total_erros
FROM importacao
ORDER BY id DESC
LIMIT 5;

SELECT id, importacao_id, status, xml_path, storage_object_key, access_key, model, issue_date, erro_codigo
FROM import_item
WHERE importacao_id = IMPORT_ID
ORDER BY id;

SELECT id, tenant_id, empresa_id, model, access_key, issue_date, emit_cnpj, dest_cnpj, total_amount, xml_path, xml_hash
FROM fiscal_document
ORDER BY id DESC
LIMIT 5;

SELECT id, tenant_id, empresa_id, access_key, fiscal_document_id
FROM fiscal_document_registry
ORDER BY id DESC
LIMIT 5;
```

## 8) Cenário negativo (422)
Manifesto com `objectKey` inexistente deve retornar `422`.

```bash
curl -i -X POST "http://localhost:8080/imports/manifest" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 99,
    "empresaId": 99,
    "entries": [
      {
        "objectKey": "99/99/nao-existe.xml",
        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      }
    ]
  }'
```

Esperado:
- HTTP `422 Unprocessable Entity`
- code `UNPROCESSABLE_ENTITY`
