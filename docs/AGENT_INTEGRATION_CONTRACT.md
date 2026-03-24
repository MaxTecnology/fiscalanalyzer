# FiscalAnalyzer — Agent Integration Contract

Este documento é a fonte de verdade para tudo que impacta o **Agente C#**.
Sempre que o backend mudar algo que afete integração, atualizar este arquivo.

Data da última atualização: **2026-03-22**
Versão do contrato: **v1.1**

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

### 2.1 POST `/imports/manifest`

Também aceito em `/api/importacoes/manifest`.

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
- `422 UNPROCESSABLE_ENTITY`: `objectKey` não existe no storage
- `500 INFRA_ERROR` ou `500 INTERNAL_ERROR`: falha de infra/inesperada

### 2.2 GET `/imports/{id}`

Retorna resumo da importação, incluindo contadores por status de item.

### 2.3 GET `/imports/{id}/items?status=&page=&size=`

Retorna itens da importação paginados.

### 2.4 GET `/documents/{accessKey}?tenantId=&empresaId=`

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

1. Autenticação por credencial do agente (API key/JWT).
2. `tenantId/empresaId` derivados da credencial (remover do body no futuro).
3. Validação obrigatória de prefixo no `objectKey`.
4. Auditoria de `agentId/clientId` na importação.

Quando qualquer item acima entrar, atualizar este arquivo e versionar para `v1.2+`.

---

## 8. Política de compatibilidade

- Toda mudança com impacto no agente deve:
  - atualizar este arquivo
  - atualizar `AGENT_ARCHITECTURE.md` se mudar fluxo
  - informar versão do contrato (`vX.Y`)
- Evitar quebra abrupta de payload.
- Em mudança breaking, manter janela de transição documentada.
