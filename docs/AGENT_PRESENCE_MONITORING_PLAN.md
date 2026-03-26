# FiscalAnalyzer — Plano de Monitoramento de Presença do Agent

Data de referência: **2026-03-26**

Objetivo: ter visão operacional confiável de agentes ativos por tenant/empresa,
com status para o front administrativo e base para alertas.

## 1. Problema atual

Hoje já existe:

- `POST /agent/session`
- `ultimoUsoAt` em `agent_api_key`
- auditoria em `agent_auth_audit` e `agent_upload_audit`

Limitação:

- não existe read model por instância de agente (`agentId`) com `last_seen`
- front não consegue responder com precisão: “quais agentes estão online agora?”

## 2. Modelo alvo (profissional)

## 2.1 Read model de presença

Tabela dedicada `agent_instance_status` com:

- escopo: `tenant_id`, `empresa_id`, `agent_id`
- vínculo operacional: `api_key_id`, `key_prefix`
- saúde: `status`, `last_seen_at`, `started_at`
- telemetria do agente: `uploads_in_flight`, `pending_files`, `error_count_window`
- contexto: `agent_version`, `hostname`, `ip_hash`
- rastros úteis: `last_upload_at`, `last_manifest_at`, `last_error_code`, `last_error_message`

## 2.2 Protocolo

- `POST /agent/session`: bootstrap do ciclo
- `POST /agent/heartbeat`: atualização periódica de presença

## 2.3 Regras de status

- `ONLINE`: heartbeat recente
- `DEGRADED`: janela intermediária sem heartbeat ou erro no agente
- `OFFLINE`: sem heartbeat além da janela crítica
- `BLOCKED` e `REVOKED`: reservados para evolução operacional de segurança

## 3. Entregas por fase

## Fase A — Base de presença (iniciada)

- [x] migration `V21` com tabela `agent_instance_status`
- [x] serviço `AgentPresenceService` (upsert + refresh de status por scheduler)
- [x] `POST /agent/heartbeat`
- [x] `POST /agent/session` atualizando presença
- [x] endpoints admin de leitura:
  - `GET /admin/empresas/{empresaId}/agents` (paginado + filtro por status)
  - `GET /admin/empresas/{empresaId}/agents/summary`
  - `GET /admin/empresas/{empresaId}/agents/{agentId}`
- [x] testes unitários/controller da base de presença

## Fase B — Front e UX operacional

- [ ] tela “Agents” com filtros por `tenant/empresa/status`
- [ ] badges de saúde (`ONLINE/DEGRADED/OFFLINE`)
- [ ] detalhe por agente (telemetria + últimos erros)

## Fase C — Observabilidade e alertas

- [ ] métricas Prometheus de presença (`online`, `offline`, `heartbeat_lag`)
- [ ] dashboard Grafana operacional
- [ ] alertas (agente offline > N min; erro elevado por agente)

## 4. Configuração recomendada

Variáveis de ambiente:

- `APP_AGENT_PRESENCE_HEARTBEAT_INTERVAL_SECONDS` (default: `30`)
- `APP_AGENT_PRESENCE_DEGRADED_AFTER_SECONDS` (default: `90`)
- `APP_AGENT_PRESENCE_OFFLINE_AFTER_SECONDS` (default: `300`)
- `APP_AGENT_PRESENCE_STATUS_REFRESH_INTERVAL_MS` (default: `30000`)

## 5. Contrato esperado para o agent (C#)

- enviar `X-Agent-Id` estável em `/agent/session` e `/agent/heartbeat`
- enviar heartbeat periódico com payload mínimo:
  - `agentVersion`
  - `hostname`
  - `uploadsInFlight`
  - `pendingFiles`
  - `errorCountWindow`

## 6. Observação de compatibilidade

- se `X-Agent-Id` não for enviado, backend usa fallback `api-key-{id}`.
- fallback mantém compatibilidade, mas reduz precisão para múltiplas instâncias.
