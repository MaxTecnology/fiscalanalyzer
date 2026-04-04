# Handoff Oficial — Equipes Agent e Front

Objetivo: permitir onboarding sem mensagem adicional fora da documentação.

Data de referência: 2026-03-25

---

## 1. Regras de alinhamento obrigatórias (ambas as equipes)

- Rotas `/admin/**` usam **JWT de usuário com role `ADMIN`**.
- `X-Admin-Token` **não é mais usado**.
- Bootstrap inicial de ambiente novo:
  - `POST /auth/bootstrap-admin` com header `X-Bootstrap-Token`.
- Contrato do agent permanece por ApiKey Bearer:
  - `POST /agent/session`
  - `POST /agent/upload-url`
  - `POST /imports/manifest`

---

## 2. Pacote oficial para equipe Agent

Leitura obrigatória (ordem):

1. `docs/DOCS_INDEX.md`
2. `docs/AGENT_INTEGRATION_CONTRACT.md`
3. `docs/AGENT_ARCHITECTURE.md`
4. `docs/FiscalAnalyzer.routes.http`
5. `docs/SMOKE_TEST_HOMOLOG_IDENTITY_PIPELINE.md`
6. `docs/SECURITY_OPERATIONS_RUNBOOK.md`
7. `docs/DEPLOY_DOCKPLOY.md`

Objetivo do pacote:
- ajustar o agent para o contrato vigente;
- validar cenários de `429` (`Retry-After`), `409` e `422`;
- fechar smoke em homolog sem dependência de instrução verbal.

---

## 3. Pacote oficial para equipe Front

Leitura obrigatória (ordem):

1. `docs/DOCS_INDEX.md`
2. `docs/README_ARCHITECTURE.md`
3. `docs/FRONTEND_SPEC.md`
4. `docs/DOCUMENTS_VIEWER_API_CONTRACT.md`
5. `docs/NEXT_STEPS_AGENT_FRONT.md`
6. `docs/FiscalAnalyzer.routes.http`
7. `docs/TENANT_EMPRESA_USUARIO_DECISION.md`
8. `docs/IDENTITY_FOUNDATION_CHECKLIST.md`
9. `docs/DATABASE.md`
10. `docs/DOMAIN.md`
11. `docs/CONVENTIONS.md`

Objetivo do pacote:
- iniciar front do zero com APIs e regras atuais;
- evitar implementação sobre contrato obsoleto;
- garantir fluxo admin + monitoramento de importações.

---

## 4. Critério de aceite do handoff

Handoff é considerado completo quando:

- equipe Agent confirma execução do contrato atual sem erro de autenticação/autorização;
- equipe Front confirma consumo das rotas com JWT e RBAC;
- ambos usam exclusivamente os documentos listados acima.
