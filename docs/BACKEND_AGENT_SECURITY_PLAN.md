# Plano de Segurança — Backend ↔ Agent (v2)

Data: 2026-03-25  
Status: **Backend estabilizado nas Fases 1-3 e integrado à identidade local (JWT/RBAC)**

---

## 1. Objetivo

Eliminar segredos de infraestrutura no agente e centralizar autenticação/autorização no backend, sem quebrar o pipeline atual (`manifest -> parse assíncrono`).

Objetivos diretos:
- remover `TenantId`, `EmpresaId` e credenciais S3 do cliente
- autenticar agente por `ApiKey` revogável
- manter idempotência e rastreabilidade por empresa/tenant
- preparar base para painel administrativo (front)

---

## 2. Estado atual

Hoje:
- `POST /imports/manifest` exige Bearer ApiKey e valida `tenantId/empresaId` do body contra a chave
- `POST /agent/session` e `POST /agent/upload-url` implementados
- upload por URL pré-assinada já elimina credenciais S3 no agente
- gestão de chave por endpoints admin implementada (create/list/revoke/rotate)
- auditoria mínima de `upload-url` implementada (`agent_upload_audit`)
- auditoria dedicada de autenticação implementada (`agent_auth_audit`)
- rate-limit/lockout distribuído via Redis com fallback in-memory
- retenção automática das tabelas de auditoria por job agendado (`AgentAuditCleanupService`)

Risco residual atual:
- rotação segura de segredos (`APP_SECURITY_BOOTSTRAP_TOKEN`, `APP_SECURITY_JWT_SECRET`) por ambiente
- observabilidade contínua de lockout/rate-limit em produção

---

## 3. Arquitetura alvo

Configuração mínima do agente:

```json
{
  "Agent": {
    "BackendBaseUrl": "https://api.fiscalanalyzer.com.br",
    "ApiKey": "fa_live_xxxxxxxxxxxxxxxxxxxxxxxx",
    "WatchDirectory": "C:\\NFe\\XMLs"
  }
}
```

Fluxo alvo:
1. `POST /agent/session` (Bearer ApiKey)
2. `POST /agent/upload-url` para cada arquivo novo
3. `PUT` no pre-signed URL (upload direto no S3/B2)
4. `POST /imports/manifest` autenticado
5. backend processa normalmente via fila/workers

---

## 4. Endpoints novos/alterados

## 4.1 `POST /agent/session`

Entrada:
- Header: `Authorization: Bearer fa_live_...`

Saída:
- `tenantId`, `empresaId`, metadados de empresa
- opcional: `scanIntervalSeconds`, `maxUploadConcurrency`

Erros:
- `401` chave inválida
- `403` chave revogada/inativa/empresa inativa

Regras:
- `tenant/empresa` sempre derivados da ApiKey (não do body)
- registrar auditoria de uso

## 4.2 `POST /agent/upload-url`

Entrada:
- Header Bearer
- body: `sha256`, `originalFileName`, `sizeBytes`

Saída:
- `uploadUrl`, `objectKey`, `expiresIn`

Erros:
- `401` chave inválida
- `403` chave sem permissão
- `409` objeto já existe (idempotência de upload)

Regras:
- `objectKey` determinado no backend: `{tenantId}/{empresaId}/{sha256}.xml`
- URL com expiração curta (recomendado: 900s)
- registrar tentativa e resultado de emissão

## 4.3 `POST /imports/manifest` (alteração)

Passa a exigir Bearer ApiKey para fluxo agente.

Regra de transição:
- fase inicial: manter `tenantId/empresaId` no body e validar cruzado com a ApiKey
- fase final: remover dependência desses campos no body para agentes novos

Erros adicionais:
- `403` se mismatch entre body e tenant/empresa da chave

## 4.4 Endpoints administrativos (painel)

- `POST /admin/empresas/{id}/agent-keys`
- `GET /admin/empresas/{id}/agent-keys`
- `DELETE /admin/empresas/{id}/agent-keys/{keyId}`
- `POST /admin/empresas/{id}/agent-keys/{keyId}/rotate`

Requisitos:
- autenticação de usuário/admin separada da ApiKey do agente
- RBAC obrigatório
- chave completa exibida apenas na criação

---

## 5. Modelo de dados

Tabela principal sugerida: `agent_api_key`

Campos mínimos:
- `id` PK
- `tenant_id`
- `empresa_id`
- `key_hash` (SHA-256 do segredo completo)
- `key_prefix` (ex.: `fa_live_ab12`)
- `descricao`
- `ativo`
- `ultimo_uso_at`
- `criado_at`
- `revogado_at`
- `criado_por` (usuário admin)

Regras:
- nunca persistir chave em texto puro
- comparação de hash em tempo constante
- índice por `(tenant_id, empresa_id, ativo)` e `key_prefix`

Opcional recomendado:
- tabela de auditoria (`agent_auth_audit`) para tentativas de auth/session/upload-url

---

## 6. Controles de segurança obrigatórios

- autenticação Bearer em todos os endpoints de agente
- rate-limit por ApiKey (principal) e por fingerprint anônimo (secundário)
- lockout temporário em excesso de falha
- log estruturado sem segredos
- rotação de chave suportada sem downtime
- revogação imediata (efeito próximo do real-time)
- TTL curto para pre-signed URL
- validação de integridade por `sha256` no manifesto
- validação de prefixo de `objectKey` com tenant/empresa

Implementação atual de rate-limit:
- tráfego autenticado: bucket por `apiKeyId` e por rota (`/agent/session`, `/agent/upload-url`, `/imports/manifest`)
- tráfego sem chave válida: bucket por fingerprint (`IP + User-Agent + X-Agent-Id`)
- lockout: após limiar de falhas de autenticação no fingerprint, backend retorna `429` com `Retry-After`
- backend de persistência: **Redis** (modo distribuído) com fallback controlado para in-memory em falha do Redis

---

## 7. Estratégia de implementação por fases

## Fase 1 — Auth de agente (prioridade máxima)

Entregas:
- migration da tabela `agent_api_key`
- service de autenticação de ApiKey
- `POST /agent/session`
- proteção de `POST /imports/manifest` com bearer + cross-check tenant/empresa
- endpoints admin de key management (mínimo CRUD de chave)

Resultado esperado:
- acesso rastreável/revogável por empresa
- redução imediata do risco de spoof

## Fase 2 — Upload por pre-signed URL

Entregas:
- `POST /agent/upload-url`
- emissão de URL assinada (Backblaze S3 compatível)
- resposta `409` para objeto já existente
- ajuste do agente para upload sem credenciais S3

Resultado esperado:
- nenhuma credencial S3 no cliente

## Fase 3 — Hardening operacional

Entregas:
- rate limit por ApiKey nas rotas do agente
- proteção anônima + lockout de tentativas inválidas por fingerprint
- rotação de chave guiada por painel
- auditoria completa
- alertas de abuso
- fallback/versionamento de contrato para agentes antigos

---

## 8. Testes obrigatórios por fase

## Fase 1
- unit: hash/validação/revogação
- controller: `401`, `403`, `200` em `/agent/session`
- controller: `/imports/manifest` rejeita mismatch tenant/empresa
- integração: fluxo manifesto autenticado fim-a-fim (sem Rabbit real opcional)

## Fase 2
- unit: geração de objectKey e TTL
- controller: `/agent/upload-url` (`200`, `401`, `403`, `409`)
- integração: upload URL + manifesto + parse

## Fase 3
- testes de rotação/revogação concorrente
- testes de rate-limit
- testes de auditoria

Critério de aceite:
- `mvn test` 100% verde
- `docs/FiscalAnalyzer.routes.http` atualizado para cada endpoint novo

---

## 9. Documentação obrigatória a cada entrega

Ao final de cada fase, atualizar:
- `AGENT_INTEGRATION_CONTRACT.md` (versão do contrato)
- `AGENT_ARCHITECTURE.md` (fluxo do agente)
- `FiscalAnalyzer.routes.http` (chamadas reais)
- `README_ARCHITECTURE.md` (status da fase)
- `SECURITY_OPERATIONS_RUNBOOK.md` (monitoramento e resposta)

---

## 10. Decisões pendentes (fase 7+)

- política de expiração de ApiKey (sem expiração no baseline vs expiração periódica opcional)
- granularidade de permissão por chave (empresa única atual vs chaves multi-empresa no futuro)
- estratégia de transição para remoção definitiva de `tenantId/empresaId` do body do manifesto
- evolução para OIDC/SSO no front administrativo

---

## 11. Resumo executivo

Ordem recomendada daqui em diante:
1. estabilizar rollout front administrativo sobre as APIs JWT/RBAC já ativas;
2. manter contrato do agent estável durante homologação e carga real;
3. medir e ajustar limites de rate-limit/lockout por perfil de cliente;
4. planejar evolução de sessão (refresh token) e/ou OIDC.

O backend já está pronto para operação de ingestão com governança de acesso administrativa via usuário local + JWT.
