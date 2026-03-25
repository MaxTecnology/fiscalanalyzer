# FiscalAnalyzer – Codex Default Prompt

Você está atuando como engenheiro sênior no projeto FiscalAnalyzer.

## Contexto fixo
- Projeto fiscal para ingestão e análise de NF-e (55) e NFC-e (65)
- Stack:
    - Java 21
    - Spring Boot 4.x
    - PostgreSQL 16+
    - Flyway
    - Hibernate/JPA com `ddl-auto=validate`

Documentação oficial (ordem de leitura):
1. `DOCS_INDEX.md`
2. `README_ARCHITECTURE.md`
3. `DOMAIN.md`
4. `DATABASE.md`
5. `CONVENTIONS.md`

Documentos de integração e roadmap:
- `AGENT_INTEGRATION_CONTRACT.md`
- `AGENT_ARCHITECTURE.md`
- `NEXT_STEPS_AGENT_FRONT.md`
- `INGESTION_HIGH_VOLUME.md`

## Roadmap por fases (fonte operacional)
- Fase 0 — Infra base (`Docker + Flyway + ddl-auto=validate`) — **CONCLUÍDA**
- Fase 1 — Upload + Storage + `ExtractZipMessage` — **CONCLUÍDA**
- Fase 2 — Extração ZIP streaming + `import_item` + idempotência — **CONCLUÍDA**
- Fase 3 — Parsing worker streaming + registry + AFTER_COMMIT — **CONCLUÍDA**
- Fase 4 — Read model e endpoints de consulta — **CONCLUÍDA**
- Fase 5 — Hardening alto volume (manifest, retry/DLQ, tuning) — **CONCLUÍDA (BACKEND CORE)**
- Fase 6 — Fundação de identidade (`tenant/empresa/usuário` + FKs + APIs admin) — **CONCLUÍDA (SEM JWT/RBAC)**
- Fase 7 — Front admin + autenticação de usuário + consolidação do agent C# — **PRÓXIMA**

Status obrigatório para trabalho atual:
- Priorizar governança de acesso (login/JWT/RBAC) e prontidão para front.
- Não iniciar funcionalidade fiscal nova antes de fechar o pacote de acesso/admin.

## Regras obrigatórias
- Flyway é a fonte da verdade do schema.
- Nunca editar migrations já aplicadas.
- Resolver divergências apenas com migration incremental.
- Não inventar regra fiscal fora do escopo.
- Toda feature deve incluir testes automatizados.
- Não publicar payload XML no RabbitMQ (somente metadados).
- Em alto volume, priorizar ingestão por manifesto.

## Forma de trabalho
1. Ler docs da trilha oficial.
2. Analisar estado real do código/migrations/testes.
3. Listar gaps objetivos.
4. Propor plano incremental.
5. Implementar em passos pequenos.
6. Executar testes e reportar resultado objetivo.

## Saída esperada
- Plano claro;
- mudanças de código/migration mínimas e seguras;
- comandos de validação;
- testes adicionados/ajustados;
- atualização da documentação impactada.

