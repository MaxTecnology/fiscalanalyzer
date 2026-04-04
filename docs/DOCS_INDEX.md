# FiscalAnalyzer — Docs Index (Fonte de Contexto)

Este arquivo é o ponto de entrada da documentação do projeto.
Quando houver compactação de contexto, comece por aqui.

Data de referência: **2026-04-04**

---

## 1. Fonte de verdade (ordem de leitura)

1. `README_ARCHITECTURE.md`  
   Estado técnico atual, fluxos e fases.
2. `DOMAIN.md`  
   Conceitos de negócio e limites do domínio fiscal.
3. `DATABASE.md`  
   Regras de schema, Flyway, integridade e idempotência.
4. `CONVENTIONS.md`  
   Regras de desenvolvimento, testes e custo/escala.

---

## 2. Operação e validação (ativo)

- `FiscalAnalyzer.routes.http`  
  Coleção oficial de chamadas manuais (admin, agent, pipeline, leitura).
- `SMOKE_TEST_HOMOLOG_IDENTITY_PIPELINE.md`  
  Roteiro fim-a-fim para homolog/local (identidade + segurança do agent + ingestão).
- `DEPLOY_DOCKPLOY.md`  
  Deploy em nuvem com Dockploy.
- `SECURITY_OPERATIONS_RUNBOOK.md`  
  Monitoramento e resposta a incidentes de segurança/rate-limit.
- `INGESTION_HIGH_VOLUME.md`  
  Diretrizes de volume alto, custo, métricas e tuning de filas.

---

## 3. Contratos com o Agent (ativo)

- `AGENT_INTEGRATION_CONTRACT.md`  
  Contrato oficial backend ↔ agent C# (versionado).
- `AGENT_ARCHITECTURE.md`  
  Arquitetura e fluxo recomendado para o projeto do agent.
- `AGENT_PROMPT.md`  
  Guia de construção do agent C# (documento de implementação do projeto externo).
- `AGENT_PRESENCE_MONITORING_PLAN.md`
  Plano de monitoramento profissional de presença (`session`/`heartbeat`)
  e leitura operacional para front admin.

---

## 4. Governança de segurança e identidade (ativo)

- `BACKEND_AGENT_SECURITY_PLAN.md`  
  Plano técnico consolidado de segurança backend ↔ agent.
- `BACKEND_AGENT_SECURITY_CHECKLIST.md`  
  Checklist operacional por fase (segurança agent).
- `TENANT_EMPRESA_USUARIO_DECISION.md`  
  Decisão arquitetural de identidade interna.
- `IDENTITY_FOUNDATION_CHECKLIST.md`  
  Checklist de execução da fundação de identidade.

---

## 5. Backlog atual (Agent + Front)

- `FRONTEND_SPEC.md`  
  Especificação técnica oficial do front administrativo (stack, fases e contratos).
- `DOCUMENTS_VIEWER_API_CONTRACT.md`
  Contrato alvo do módulo de documentos para o front (P0/P1/P2, filtros e payloads).
- `NEXT_STEPS_AGENT_FRONT.md`  
  Backlog consolidado do que falta implementar no backend para suportar front administrativo
  e autenticação de usuários (JWT/RBAC), além dos alinhamentos de contrato com o agent.
- `HANDOFF_AGENT_FRONT.md`  
  Pacote oficial de handoff por equipe (Agent e Front), com ordem de leitura obrigatória.
- `XML_FULL_COVERAGE_PLAN.md`
  Plano oficial para evolução profissional da cobertura total de campos NF-e/NFC-e
  (parser + schema + testes + reprocesso).
- `XML_FIELD_COVERAGE_PHASE1.md`
  Matriz de cobertura da Fase 1: campos já existentes no schema e seu mapeamento
  atual no parser.
- `XML_FIELD_COVERAGE_PHASE2.md`
  Matriz de cobertura da Fase 2: campos/tabelas analíticas adicionados em
  `V20` e critérios de validação.
- `XML_FIELD_COVERAGE_PHASE3.md`
  Operação de cobertura da Fase 3: read model de cobertura e backfill
  controlado para XML direto e ZIP legado.

---

## 6. Documentos de apoio (não fonte primária)

- `MANUAL_TEST_MANIFEST_PIPELINE.md`  
  Guia específico para teste manual do fluxo de manifesto.
  Preferir `SMOKE_TEST_HOMOLOG_IDENTITY_PIPELINE.md` para validação completa.
- `CODEX_PROMPT.md`  
  Prompt operacional para execução de tarefas no repositório.
