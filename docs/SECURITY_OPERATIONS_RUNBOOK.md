# Security Operations Runbook — Agent/API

Data: 2026-03-24

Este runbook cobre monitoramento e resposta de incidente para autenticação, rate-limit e lockout.
Também cobre retenção operacional das tabelas de auditoria de segurança.

---

## 1. Indicadores (métricas)

Counters principais expostos pelo backend:
- `security.rate_limit.allowed{scope,route}`
- `security.rate_limit.blocked{scope,route}`
- `security.auth.failure{route}`
- `security.auth.lockout.started{route}`
- `security.auth.lockout.blocked{route}`
- `security.rate_limit.redis.fallback{route}`
- `security.auth.redis.fallback{route}`

Indicadores de apoio:
- `import.parse.failure`
- `queue.dlq`

---

## 2. Alertas recomendados

1. Bloqueio elevado por rota do agente
- condição: `sum(rate(security_rate_limit_blocked_total{route=~"agent.*|imports.manifest"}[5m])) > 20`
- severidade: warning
- ação: validar burst do agente e latência de rede.

2. Lockout anormal
- condição: `sum(increase(security_auth_lockout_started_total[15m])) > 5`
- severidade: critical
- ação: suspeita de chave inválida/ataque; validar origem por fingerprint e IP.

3. Fallback Redis ativo
- condição: `sum(increase(security_rate_limit_redis_fallback_total[5m])) > 0` OR `sum(increase(security_auth_redis_fallback_total[5m])) > 0`
- severidade: critical
- ação: verificar Redis (latência/conectividade) imediatamente.

4. Falhas de autenticação elevadas
- condição: `sum(rate(security_auth_failure_total[5m])) > 30`
- severidade: warning
- ação: revisar chaves revogadas/expiradas no cliente.

---

## 3. Triage rápido

1. Confirmar saúde:
- `/actuator/health`
- verificar componente Redis em `health` (details).

2. Verificar filas:
- `import.extract`, `import.parse`, DLQs.

3. Verificar logs estruturados:
- `security.rate_limit.blocked`
- `security.auth.lockout.started`
- `security.auth.audit_error`

4. Verificar auditoria no banco:
- `agent_auth_audit` (falhas auth/autorização)
- `agent_upload_audit` (emissão upload-url)

---

## 4. Ações de contenção

1. Chave comprometida/suspeita:
- criar nova chave (`POST /admin/empresas/{id}/agent-keys`)
- atualizar agente com nova chave
- revogar antiga (`DELETE /admin/empresas/{id}/agent-keys/{keyId}`) ou rotacionar (`.../rotate` com `revokeOld=true`).

2. Burst legítimo de operação:
- ajustar `APP_SECURITY_RATE_LIMIT_AGENT_*` gradualmente.
- manter `Retry-After` respeitado no agente.

3. Redis instável:
- manter backend em fallback in-memory temporário.
- restaurar Redis e confirmar queda dos contadores de fallback.

---

## 5. Pós-incidente

1. Registrar causa raiz e janela de impacto.
2. Ajustar limites e/ou políticas de lockout.
3. Atualizar este runbook com aprendizados operacionais.

---

## 6. Retenção de auditoria

Job automático:
- serviço: `AgentAuditCleanupService`
- agenda: `APP_SECURITY_AUDIT_CLEANUP_CRON` (default `0 30 3 * * *`, UTC)
- limpeza em lote (`batch`) para reduzir lock longo em tabela

Parâmetros principais:
- `APP_SECURITY_AUDIT_CLEANUP_ENABLED` (`true|false`)
- `APP_SECURITY_AUDIT_CLEANUP_AUTH_RETENTION_DAYS` (default `90`)
- `APP_SECURITY_AUDIT_CLEANUP_UPLOAD_RETENTION_DAYS` (default `30`)
- `APP_SECURITY_AUDIT_CLEANUP_BATCH_SIZE` (default `5000`)
- `APP_SECURITY_AUDIT_CLEANUP_MAX_BATCHES` (default `100`)

Validação operacional:
1. Verificar logs: `security.audit.cleanup.completed`.
2. Verificar métrica: `security_audit_cleanup_deleted_total{table=...}`.
3. Consultar volume residual:
   - `select count(*) from agent_auth_audit;`
   - `select count(*) from agent_upload_audit;`
