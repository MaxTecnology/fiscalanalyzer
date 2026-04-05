# FiscalAnalyzer — Próximos Passos (Agent + Front)

Objetivo: consolidar o que ainda falta no backend antes de iniciar front administrativo e rollout final do agent.

Data de referência: **2026-04-04**

---

## 1. Estado atual consolidado

Implementado no backend:
- pipeline ZIP e MANIFEST funcional (upload/extract/parse/read model);
- fundação de ingestão SPED (`POST /sped/files/upload`) com suporte a `.txt` e `.zip` com múltiplos `.txt`;
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
- módulo de documentos conforme contrato em `DOCUMENTS_VIEWER_API_CONTRACT.md`:
  - P0 concluído no backend (`/documents` listagem, detalhe enriquecido e download XML);
  - P1 parcialmente concluído (`summary` e `events` ativos; `export` pendente).

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

## Fase 8.1 — SPED Fiscal (fundação concluída)

Escopo:
- ingestão EFD ICMS/IPI com upload `.txt` e `.zip` (múltiplos `.txt`);
- persistência de `sped_fiscal_file`;
- enfileiramento AFTER_COMMIT em `import.sped.parse`.

## Fase 8.2 — SPED Fiscal (próxima)

Escopo:
- parser streaming de registros SPED (`0000`, `0150`, `C100`, `C170`, `C190`, `E110`, `E111`, `9999`);
- conciliação SPED x XML importado (nota, itens e impostos).

Status atual:
- worker de parse SPED já ativo com leitura streaming para `TXT` e `ZIP_ENTRY`;
- parser SPED já persiste blocos: `0150`, `C100`, `C170`, `C190`, `E110`, `E111` + metadados (`0000`/`9999`);
- read model SPED inicial disponível para front:
  - `GET /sped/files`
  - `GET /sped/files/{id}`
  - `GET /sped/files/{id}/reconciliation`
  - `GET /sped/summary`
  - `POST /sped/files/{id}/reprocess`

Próximo passo objetivo:
- integrar front SPED com:
  - resumo `GET /sped/reconciliation/summary`;
  - drill-down `GET /sped/reconciliation/divergences`.

Documento base:
- `SPED_FISCAL_IMPLEMENTATION_PLAN.md`

Critério de aceite:
- relatório de divergências por arquivo/período disponível por API;
- sem regressão do pipeline atual de XML (ZIP/MANIFEST).

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
