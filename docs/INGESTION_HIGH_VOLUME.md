# FiscalAnalyzer – Ingestão em Alto Volume

Este documento descreve o fluxo recomendado para lotes muito grandes
(ex.: milhões de XML por dia).

---

## 1. Objetivo

Permitir ingestão contínua de XML fiscal com:
- throughput alto
- custo previsível em storage S3-compatível
- reprocessamento seguro por item

---

## 2. Papel do Agente

**Agente** = aplicativo local (desktop ou serviço headless) no ambiente do cliente.

Responsabilidades:
- monitorar diretórios locais
- fazer upload concorrente para bucket
- calcular hash (SHA-256)
- gerar manifesto de lote
- chamar API do backend para iniciar processamento

Não é responsabilidade do agente:
- aplicar regra fiscal
- publicar diretamente no RabbitMQ

---

## 3. Fluxo recomendado

1. Agente descobre XMLs locais.
2. Agente envia XMLs para storage (`bucket/key` determinístico).
3. Agente gera manifesto com metadados (`object_key`, `sha256`, `size`, `tenant_id`, `empresa_id`).
4. Backend registra `importacao`.
5. Backend cria `import_item` em batch com base no manifesto.
6. Backend publica mensagens de parse com metadados.
7. Worker de parse lê XML direto do storage.
8. Dados fiscais são persistidos no PostgreSQL.

---

## 4. Guardrails de custo (S3/B2)

- Não enviar conteúdo XML em mensagens da fila.
- Evitar `ListObjects` como forma principal de descoberta.
- Evitar `HeadObject` redundante por arquivo.
- Ler cada XML somente uma vez no parse.
- Usar lifecycle para retenção de bruto quando aplicável.

---

## 5. Observabilidade mínima

### 5.1 Métricas já instrumentadas (Micrometer)

| Métrica | Tipo | Tags | Descrição |
|---|---|---|---|
| `import.parse.success` | Counter | — | XMLs parseados com sucesso |
| `import.parse.failure` | Counter | — | XMLs com falha de parse |
| `import.parse.duplicate` | Counter | — | XMLs duplicados (idempotência) |
| `import.parse.duration` | Timer | — | Tempo por operação de parse |
| `import.extract.items.created` | Counter | — | Itens criados por extração de ZIP |
| `import.extract.duration` | Timer | — | Tempo por operação de extração |
| `queue.retry` | Counter | `queue` | Tentativas de retry por fila |
| `queue.dlq` | Counter | `queue` | Mensagens encaminhadas para DLQ |

Exposição via Spring Actuator: `GET /actuator/metrics/{nome}`

### 5.2 O que falta para produção

- **Queue depth / consumer lag**: monitorar backlog via RabbitMQ Management API
  (`GET /api/queues/%2F/{nome}` → campo `messages`). Alertar se acima de threshold.
- **Distributed tracing**: sem Micrometer Tracing/OTLP, correlationId está apenas nos logs.
- **Dashboard**: exportar métricas para Prometheus + Grafana (ou compatível)
  adicionando `micrometer-registry-prometheus` e configurando scrape.

### 5.3 Fluxo de DLQ (implementado)

```
ParseXmlConsumer → falha → retry (até 5x, backoff exponencial)
    → QueueRetryRecoverer → AmqpRejectAndDontRequeueException
    → RabbitMQ move para import.parse.dlq
    → ParseDlqConsumer → DlqHandlerService.markItemFalhaPermanente()
    → import_item.status = FALHA_PERMANENTE

ExtractZipConsumer → falha → retry (até 5x)
    → RabbitMQ move para import.extract.dlq
    → ExtractDlqConsumer → DlqHandlerService.markImportacaoFalha()
    → importacao.status = FALHA
```

Status `FALHA_PERMANENTE` aparece nos endpoints de consulta de itens e
pode ser filtrado via `GET /imports/{id}/items?status=FALHA_PERMANENTE`.

---

## 6. Testes obrigatórios para mudanças

- teste de caminho feliz
- teste de falha de infra (retry)
- teste de idempotência
- teste de lote vazio/manifesta inválido (quando aplicável)

---

## 7. Baseline atual (2026-03-21)

Cenário executado:
- endpoint: `POST /imports/upload`
- arquivo: `docs/nfe.zip` (50 XML)
- ambiente: profile `local`, pipeline completo (upload -> extract -> parse)

Resultado observado:
- execução com `tenantId=99` e `empresaId=99`:
  - `status=CONCLUIDO`
  - `totalItems=50`
  - `statusCounts=PARSEADO: 50`
  - tempo até conclusão: `~2s`
- execução após tuning inicial de listeners (`tenantId=100`, `empresaId=100`):
  - `status=CONCLUIDO`
  - `statusCounts=PARSEADO: 50`
  - tempo até conclusão: `~1s`
- execução repetida para mesmo conjunto (tenant/empresa já processado):
  - `status=CONCLUIDO`
  - `statusCounts=DUPLICADO: 50`
  - confirma idempotência por `access_key` no registry

Observações:
- o endpoint `GET /imports/{id}` foi consolidado no `ImportacaoReadController` para evitar ambiguidade de rota.
- para medir throughput de parse real, usar `tenantId/empresaId` ainda não utilizados.

Comando de validação rápida:
```bash
./mvnw -q -Dspring-boot.run.profiles=local spring-boot:run

curl -F tenantId=100 -F empresaId=100 -F file=@docs/nfe.zip \
  http://localhost:8080/imports/upload

curl http://localhost:8080/imports/{importacaoId}
```

---

## 8. Tuning inicial de filas (fase 5)

Parâmetros atuais em `application.yaml`:
- `app.queue.listener.extract.concurrency=1`
- `app.queue.listener.extract.max-concurrency=2`
- `app.queue.listener.extract.prefetch=10`
- `app.queue.listener.parse.concurrency=2`
- `app.queue.listener.parse.max-concurrency=8`
- `app.queue.listener.parse.prefetch=50`

Objetivo:
- manter extração mais conservadora (I/O de ZIP)
- permitir parse escalar mais que extração
- reduzir roundtrips de broker com prefetch explícito
