# Checklist de Execução — Segurança Backend ↔ Agent

Status: **Fases 1-3 concluídas; manter como checklist de regressão operacional**  
Regra: marcar somente quando código + testes + documentação estiverem concluídos.

---

## Fase 1 — Autenticação e gestão de ApiKey

- [x] Criar migration `V13__create_agent_api_key.sql`
- [x] Criar entidade/repository/service de `agent_api_key`
- [x] Implementar hash seguro da chave (`SHA-256`) sem persistir plaintext
- [x] Implementar geração de chave (`fa_live_...`) e exibição única
- [x] Implementar `POST /admin/empresas/{id}/agent-keys`
- [x] Implementar `GET /admin/empresas/{id}/agent-keys`
- [x] Implementar `DELETE /admin/empresas/{id}/agent-keys/{keyId}`
- [x] Implementar autenticação Bearer para rotas do agente
- [x] Implementar `POST /agent/session`
- [x] Proteger `POST /imports/manifest` com bearer
- [x] Validar cross-check `tenantId/empresaId` body vs ApiKey
- [x] Registrar `ultimo_uso_at` da chave
- [x] Logs estruturados (sem segredo)
- [x] Testes unitários da autenticação
- [x] Testes controller (`401`, `403`, `200`) de `/agent/session`
- [x] Testes controller de `/imports/manifest` autenticado
- [x] Atualizar `docs/FiscalAnalyzer.routes.http` com rotas reais da fase
- [x] Atualizar `docs/AGENT_INTEGRATION_CONTRACT.md` (versão nova)

Critério de aceite da fase:
- [x] `./mvnw -q test` verde
- [x] contrato do agente versionado
- [x] rotas `.http` executáveis

---

## Fase 2 — Upload por pre-signed URL

- [x] Implementar `POST /agent/upload-url`
- [x] Gerar `objectKey` determinístico no backend
- [x] Definir TTL da URL assinada (ex.: 900s)
- [x] Retornar `409` quando objeto já existir
- [x] Validar limites de tamanho/tipo no request
- [x] Auditoria mínima do endpoint (`tenant/empresa/keyPrefix/result`)
- [x] Testes unitários de geração de objectKey/TTL
- [x] Testes controller (`200`, `401`, `403`, `409`)
- [x] Teste integração do fluxo upload-url -> manifest -> parse
- [x] Atualizar `docs/FiscalAnalyzer.routes.http` com rota real
- [x] Atualizar `docs/AGENT_ARCHITECTURE.md` e contrato

Critério de aceite da fase:
- [x] agente não precisa mais de credencial S3
- [x] `./mvnw -q test` verde

---

## Fase 3 — Hardening e operação

- [x] Implementar rate-limit por ApiKey/IP
- [x] Implementar lockout temporário por falhas repetidas
- [x] Implementar backend distribuído de rate-limit/lockout (Redis)
- [x] Implementar auditoria dedicada (`agent_auth_audit`) se necessário
- [x] Implementar retenção automática de auditoria (`agent_auth_audit`/`agent_upload_audit`)
- [x] Implementar rotação de chave sem downtime
- [x] Implementar alertas/indicadores de abuso
- [x] Testes de concorrência (revogação/rotação em uso)
- [x] Testes de rate-limit e lockout
- [x] Atualizar documentação operacional

Critério de aceite da fase:
- [x] segurança operacional monitorável
- [x] runbook de incidente publicado

---

## Atualizações obrigatórias por entrega

- [x] `docs/BACKEND_AGENT_SECURITY_PLAN.md`
- [x] `docs/FiscalAnalyzer.routes.http`
- [x] `docs/AGENT_INTEGRATION_CONTRACT.md`
- [x] `docs/README_ARCHITECTURE.md` (status da fase)

---

## Controle de qualidade (global)

- [x] Sem editar migrations já aplicadas
- [x] Novas mudanças apenas por migration incremental
- [x] `ddl-auto=validate` mantido
- [x] Sem payload XML em RabbitMQ
- [x] Logs sem segredos
