# FiscalAnalyzer — Próximos Passos (Agent + Front)

Objetivo: consolidar o que ainda falta no backend antes de iniciar front administrativo e rollout final do agent.

Data de referência: **2026-03-25**

---

## 1. Estado atual consolidado

Implementado no backend:
- pipeline ZIP e MANIFEST funcional (upload/extract/parse/read model);
- segurança do agent com ApiKey, upload URL, rate-limit/lockout distribuído, DLQ e auditoria;
- fundação de identidade (`tenant`, `empresa`, `app_user`, roles e vínculos);
- autenticação local:
  - `POST /auth/login` (JWT)
  - `GET /auth/me`
  - `POST /auth/bootstrap-admin` (ambiente novo)
- autorização:
  - `/admin/**` exige JWT com role `ADMIN` (sem `X-Admin-Token`);
  - `POST /imports/upload` exige `ADMIN`/`OPERADOR` + escopo empresa;
  - leitura de `imports`/`documents` exige `ADMIN`/`OPERADOR`/`LEITOR` + escopo.

---

## 2. Backlog ativo

## Fase 7.3 — Front administrativo

Escopo mínimo:
- login;
- cadastro/listagem de tenant e empresa;
- cadastro/listagem de usuários, roles e vínculos de empresa;
- gestão de Agent API Key (criar/listar/revogar/rotacionar);
- monitoramento básico de importações e itens.

Critério de aceite:
- front usa apenas APIs oficiais de `docs/FiscalAnalyzer.routes.http`;
- smoke test manual fechado em homolog.

## Fase 7.4 — Consolidação do Agent C#

Escopo:
- alinhar o agent ao contrato atual em `AGENT_INTEGRATION_CONTRACT.md`;
- validar comportamento para `429` (`Retry-After`), `409` e `422`;
- validar execução concorrente em lote grande com monitoramento de DLQ.

Critério de aceite:
- execução estável sem crescimento anômalo de `import.extract.dlq` e `import.parse.dlq`.

## Fase 7.5 — Sessão de usuário (opcional para primeira entrega)

Escopo:
- refresh token/rotação de sessão;
- política de revogação de sessão;
- endurecimento de claims/token policy.

Critério de aceite:
- sem impacto no contrato atual do agent.

---

## 3. Documentação obrigatória por entrega

Sempre atualizar:
- `README_ARCHITECTURE.md`;
- `FiscalAnalyzer.routes.http`;
- `SMOKE_TEST_HOMOLOG_IDENTITY_PIPELINE.md`;
- `AGENT_INTEGRATION_CONTRACT.md` (quando houver impacto no agent);
- `DATABASE.md` (quando houver migration nova).

---

## 4. Guardrails

- não introduzir feature fiscal nova antes de fechar front/agent operacional;
- não quebrar contrato do agent sem versionar transição;
- não usar `ddl-auto=update/create`;
- não editar migrations já aplicadas.
