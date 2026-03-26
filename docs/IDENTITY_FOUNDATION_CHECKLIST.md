# Checklist de Execução — Fundação de Identidade (Tenant/Empresa/Usuário)

Data: 2026-03-25  
Status: **Fases A-E concluídas no escopo atual**

---

## Fase A — Schema base

- [x] Criar migration para tabela `tenant`
- [x] Criar migration para tabela `empresa` com FK para `tenant`
- [x] Criar migration para `app_user`
- [x] Criar migration para `app_role`
- [x] Criar migration para `app_user_role`
- [x] Criar migration para `app_user_empresa`
- [x] Adicionar índices (`empresa.tenant_id`, `empresa.cnpj`, `app_user.email`)

---

## Fase B — Integridade referencial nas tabelas existentes

- [x] `importacao` referenciando `empresa (id, tenant_id)`
- [x] `fiscal_document` referenciando `empresa (id, tenant_id)`
- [x] `fiscal_document_registry` referenciando `empresa (id, tenant_id)`
- [x] `agent_api_key` referenciando `empresa (id, tenant_id)`
- [x] `agent_upload_audit` referenciando `empresa (id, tenant_id)`
- [x] `agent_auth_audit` referenciando `empresa (id, tenant_id)`
- [x] estratégia de transição: constraints criadas como `NOT VALID` e validadas automaticamente quando não há órfãos

---

## Fase C — Validação de domínio no backend

- [x] Validar `tenantId/empresaId` no upload ZIP
- [x] Validar `tenantId/empresaId` no manifesto
- [x] Validar `tenantId/empresaId` nas rotas de leitura fiscal
- [x] Validar `tenantId/empresaId` na gestão de ApiKey
- [x] Erros padronizados (`403` para inativo, `422` para inexistente)

---

## Fase D — APIs administrativas de identidade

- [x] CRUD de `tenant`
- [x] CRUD de `empresa`
- [x] CRUD de `usuário` local
- [x] Vínculo usuário ↔ empresa
- [x] Vínculo usuário ↔ papéis
- [x] Atualizar `docs/FiscalAnalyzer.routes.http`

---

## Fase E — Segurança de usuário

- [x] Login local (senha hash forte)
- [x] JWT para front admin
- [x] RBAC por papel
- [x] Escopo por empresa
- [x] Descontinuação de `X-Admin-Token` (admin somente via JWT)

---

## Operação (homolog/dev)

- [ ] Backup opcional do banco atual (por ambiente)
- [ ] Recriar banco limpo conforme decisão (quando aplicável)
- [ ] Cadastrar tenant/empresa iniciais
- [ ] Rodar smoke test do pipeline (`upload-url`/`manifest`/`parse`)

---

## Qualidade

- [x] `./mvnw -q test` verde
- [x] Novas regras cobertas por testes unitários/integrados
- [x] `ddl-auto=validate` mantido
- [x] Nenhuma migration aplicada foi editada
