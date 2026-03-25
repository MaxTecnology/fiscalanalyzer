# FiscalAnalyzer — Agent Integration Contract

Este documento é a fonte de verdade para tudo que impacta o **Agente C#**.
Sempre que o backend mudar algo que afete integração, atualizar este arquivo.

Data da última atualização: **2026-03-25**
Versão do contrato: **v1.5**

---

## 1. Escopo do agente

O agente deve:
- enviar XML para storage S3-compatível (ex.: Backblaze B2 S3 API)
- enviar manifesto para API
- consultar status da importação

O agente não deve:
- publicar no RabbitMQ
- parsear XML fiscal
- escrever diretamente no banco

---

## 2. Endpoints usados pelo agente

### 2.1 POST `/agent/session`

Header obrigatório:
- `Authorization: Bearer {ApiKey}`

Header recomendado:
- `X-Agent-Id: <id-estável-do-agente>` (melhora fingerprint para lockout/rate-limit)

Request:
```json
{}
```

Response `200`:

```json
{
  "tenantId": 99,
  "empresaId": 99,
  "scanIntervalSeconds": 60,
  "maxUploadConcurrency": 4
}
```

Erros:
- `401 AUTH_UNAUTHORIZED`: ApiKey ausente/inválida
- `403 AUTH_FORBIDDEN`: ApiKey revogada/inativa
- `429 RATE_LIMITED`: limite de requisições excedido (usar header `Retry-After`)

### 2.2 POST `/imports/manifest`

Também aceito em `/api/importacoes/manifest`.

Header obrigatório:
- `Authorization: Bearer {ApiKey}`

Header recomendado:
- `X-Agent-Id: <id-estável-do-agente>`

Request:

```json
{
  "tenantId": 99,
  "empresaId": 99,
  "entries": [
    {
      "objectKey": "99/99/nfe-001.xml",
      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "sizeBytes": 14320,
      "originalFileName": "nfe-001.xml"
    }
  ]
}
```

Validações:
- `tenantId` e `empresaId`: obrigatórios, `>= 1`
- `entries`: obrigatório e não vazio
- `entries[].objectKey`: obrigatório
- `entries[].sha256`: obrigatório, 64 hex
- `entries[].sizeBytes`: opcional, se enviado deve ser `>= 0`
- `objectKey` duplicado no mesmo manifesto: rejeitado
- `objectKey` inexistente no storage: rejeitado

Response `202`:

```json
{
  "importacaoId": 42,
  "status": "RECEBIDO",
  "totalItems": 1
}
```

Erros:
- `400 VALIDATION_ERROR`: payload inválido
- `401 AUTH_UNAUTHORIZED`: header Bearer ausente/inválido
- `403 AUTH_FORBIDDEN`: ApiKey revogada/inativa ou tenant/empresa do body não conferem com a ApiKey
- `429 RATE_LIMITED`: limite de requisições excedido (usar header `Retry-After`)
- `422 UNPROCESSABLE_ENTITY`: `objectKey` não existe no storage
- `500 INFRA_ERROR` ou `500 INTERNAL_ERROR`: falha de infra/inesperada

### 2.3 POST `/agent/upload-url`

Header obrigatório:
- `Authorization: Bearer {ApiKey}`

Header recomendado:
- `X-Agent-Id: <id-estável-do-agente>`

Request:

```json
{
  "sha256": "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869",
  "originalFileName": "nfe-001.xml",
  "sizeBytes": 1500
}
```

Response `200`:

```json
{
  "uploadUrl": "https://...assinada...",
  "objectKey": "99/99/9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869.xml",
  "expiresIn": 900
}
```

Erros:
- `400 VALIDATION_ERROR`: payload inválido (hash/tamanho/nome)
- `401 AUTH_UNAUTHORIZED`: header Bearer ausente/inválido
- `403 AUTH_FORBIDDEN`: ApiKey revogada/inativa
- `429 RATE_LIMITED`: limite de requisições excedido (usar header `Retry-After`)
- `409 CONFLICT`: objeto já existe para o `sha256` informado
- `500 INFRA_ERROR`/`INTERNAL_ERROR`: falha de infra

### 2.4 GET `/imports/{id}`

Retorna resumo da importação, incluindo contadores por status de item.

### 2.5 GET `/imports/{id}/items?status=&page=&size=`

Retorna itens da importação paginados.

### 2.6 GET `/documents/{accessKey}?tenantId=&empresaId=`

Retorna documento mínimo persistido por chave de acesso.

---

## 3. Convenção de objectKey (agente)

Padrão recomendado:
- `{tenantId}/{empresaId}/{sha256}.xml`

Motivo:
- chave determinística
- facilita deduplicação local
- simplifica rastreabilidade

Observação:
- hoje o backend valida existência do objeto, mas ainda não impõe prefixo por tenant/empresa.
- essa validação de prefixo está planejada (ver seção 7).

---

## 4. Status relevantes para polling

`importacao.status`:
- `RECEBIDO`
- `EM_EXTRACAO` (fluxo ZIP)
- `EXTRAIDO`
- `PROCESSANDO`
- `CONCLUIDO`
- `FALHA`
- `ERRO`

`import_item.status`:
- `PENDENTE_PARSE`
- `PROCESSANDO`
- `PARSEADO`
- `DUPLICADO`
- `FALHA_PARSE`
- `FALHA_PERMANENTE`

Para acompanhamento operacional, considerar finalizado quando item estiver em:
- `PARSEADO`
- `DUPLICADO`
- `FALHA_PARSE`
- `FALHA_PERMANENTE`

---

## 5. Idempotência e comportamento esperado

- Reenvio do mesmo manifesto pode criar nova `importacao` (isso é permitido).
- Idempotência fiscal é garantida no parse por:
  - `fiscal_document_registry` com `UNIQUE (tenant_id, empresa_id, access_key)`.
- Em duplicidade fiscal:
  - item tende a finalizar como `DUPLICADO`.

Recomendação do agente:
- manter SQLite local para evitar reenvio desnecessário de arquivos/manifests.

---

## 6. Changelog de integração (somente impactos no agente)

### v1.5 — 2026-03-24
- Backend de rate-limit/lockout migrado para Redis (consistente entre réplicas).
- Recomendação formal de envio do header `X-Agent-Id` nas chamadas autenticadas.

Impacto no agente:
- manter retry com `Retry-After` em `429`.
- enviar `X-Agent-Id` estável por instalação/host.

### v1.4 — 2026-03-24
- Rate-limit e lockout básico ativados no backend para rotas do agente.
- Resposta `429 RATE_LIMITED` padronizada com header `Retry-After`.

Impacto no agente:
- Implementar retry com backoff lendo `Retry-After` quando vier `429`.
- Evitar bursts excessivos em `/agent/session`, `/agent/upload-url` e `/imports/manifest`.

### v1.3 — 2026-03-24
- `POST /agent/upload-url` implementado.
- Contrato de erro `409 CONFLICT` para upload duplicado.

Impacto no agente:
- upload deve ser feito por URL assinada (quando fluxo fase 2 estiver ativo no agente).
- em `409`, marcar arquivo como já-enviado (sem retry cego).

### v1.2 — 2026-03-24
- `POST /agent/session` implementado com autenticação Bearer.
- `POST /imports/manifest` agora exige Bearer ApiKey.
- Validação cruzada ativa: `tenantId/empresaId` do body devem bater com a ApiKey.
- Endpoints admin para gestão de ApiKey implementados:
  - `POST /admin/empresas/{empresaId}/agent-keys?tenantId=...`
  - `GET /admin/empresas/{empresaId}/agent-keys?tenantId=...`
  - `DELETE /admin/empresas/{empresaId}/agent-keys/{keyId}?tenantId=...`

Impacto no agente:
- todas as chamadas de manifesto devem enviar `Authorization: Bearer`.
- usar `/agent/session` para obter tenant/empresa no início do ciclo.

### v1.1 — 2026-03-22
- `POST /imports/manifest` implementado.
- Fluxo de parse passou a suportar XML direto do storage (sem ZIP).
- Erro `422 UNPROCESSABLE_ENTITY` para `objectKey` inexistente.
- Campo `importacao.source_type` (`ZIP`/`MANIFEST`) e `import_item.storage_object_key` ativos no backend.

Impacto no agente:
- já pode operar 100% por manifesto.
- deve tratar `422` como erro definitivo do lote/entry (sem retry cego).

### v1.0 — baseline anterior
- agente ainda sem endpoint manifesto no backend.

---

## 7. Mudanças planejadas (ainda não ativas)

Estas mudanças ainda **não** estão em produção, mas impactarão o agente quando ativadas:

1. Remoção futura de `tenantId/empresaId` do body do manifesto (derivação 100% via ApiKey).
2. Validação obrigatória de prefixo no `objectKey`.
3. Auditoria expandida por `agentId/clientId`.

---

## 8. Política de compatibilidade

- Toda mudança com impacto no agente deve:
  - atualizar este arquivo
  - atualizar `AGENT_ARCHITECTURE.md` se mudar fluxo
  - informar versão do contrato (`vX.Y`)
- Evitar quebra abrupta de payload.
- Em mudança breaking, manter janela de transição documentada.
