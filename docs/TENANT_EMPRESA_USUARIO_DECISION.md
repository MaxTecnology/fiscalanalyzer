# Tenant/Empresa/Usuário — Decisão Arquitetural Aprovada

Data: 2026-03-25  
Status: **Implementado no backend (incluindo login/JWT/RBAC no escopo atual)**

---

## 1. Contexto

O sistema já opera tecnicamente com `tenant_id` e `empresa_id`, porém sem cadastro mestre.
Isso gera risco de integridade e governança: IDs existem no dado fiscal, mas não há entidade canônica para representar cliente, empresa e usuário.

---

## 2. Decisões fechadas

1. `tenant.id` e `empresa.id` serão **internos do FiscalAnalyzer**.
2. Cadastro de usuário iniciará em modo **local** (sem OIDC nesta etapa).
3. Integridade referencial com FK será aplicada no nível mais forte possível, incluindo `fiscal_document`.
4. Em homolog/dev será adotado **reset de banco** para começar com estrutura correta (sem placeholder/backfill legado).

---

## 3. Arquitetura alvo de identidade

## 3.1 Entidades mestre

- `tenant`
  - `id` (PK interna)
  - `nome`
  - `status` (`ATIVO`/`INATIVO`)
  - `created_at`, `updated_at`

- `empresa`
  - `id` (PK interna)
  - `tenant_id` (FK -> `tenant.id`)
  - `cnpj` (normalizado)
  - `razao_social`
  - `status` (`ATIVA`/`INATIVA`)
  - `created_at`, `updated_at`

## 3.2 Entidades de acesso local

- `app_user`
  - `id`, `nome`, `email`, `password_hash`, `status`, `created_at`, `updated_at`, `last_login_at`

- `app_role`
  - `id`, `codigo` (`ADMIN`, `OPERADOR`, `LEITOR`), `descricao`

- `app_user_role`
  - vínculo N:N usuário ↔ papel

- `app_user_empresa`
  - vínculo N:N usuário ↔ empresa (escopo de acesso)

---

## 4. Regra de integridade para tabelas fiscais

Diretriz: toda tabela com `tenant_id` e `empresa_id` deve apontar para empresa válida.

Modelo técnico recomendado:
- Em `empresa`, criar `UNIQUE (id, tenant_id)`.
- Nas tabelas de domínio, criar FK composta:
  - `FOREIGN KEY (empresa_id, tenant_id) REFERENCES empresa(id, tenant_id)`.

Isso evita inconsistência do tipo “empresa de um tenant associada ao tenant errado”.

Tabelas alvo nesta etapa:
- `importacao`
- `fiscal_document`
- `fiscal_document_registry`
- `agent_api_key`
- `agent_upload_audit`
- `agent_auth_audit`

---

## 5. Plano de implementação

## Fase A — Schema de identidade

- Migration incremental para criar tabelas mestre (`tenant`, `empresa`) e usuários locais.
- Índices e constraints de unicidade (`cnpj`, `email`, etc.).
- Sem editar migrations já aplicadas.

## Fase B — FKs e validação de entrada

- Adicionar FKs compostas nas tabelas já existentes.
- Adicionar validação de existência/status ativo no backend antes de persistir:
  - `/imports/upload`
  - `/imports/manifest`
  - `/documents/{accessKey}`
  - `/admin/empresas/*/agent-keys`

## Fase C — Administração de identidade

- Endpoints admin para cadastro e manutenção:
  - `tenant`
  - `empresa`
  - `usuário`
  - associação usuário↔empresa↔papéis

## Fase D — Segurança de usuário

- Login local com senha hash forte (Bcrypt) e sessão JWT.
- RBAC por papel e escopo de empresa.
- Administração via JWT `ADMIN` (sem `X-Admin-Token`).

## 5.1 Andamento registrado

- Concluído em 2026-03-25:
  - `V17__create_identity_foundation.sql` (schema base completo)
  - `V18__add_tenant_empresa_foreign_keys.sql` (FKs compostas nas tabelas de domínio)
  - serviço de validação `tenant/empresa` ativo em:
    - `/imports/upload`
    - `/imports/manifest`
    - `/documents/{accessKey}`
    - `/admin/empresas/*/agent-keys`
    - autenticação de ApiKey do agente
  - CRUD admin de identidade:
    - `/admin/tenants`
    - `/admin/tenants/{tenantId}/empresas`
    - `/admin/users` + vínculos de role/empresa
- Concluído adicionalmente:
  - `POST /auth/login`, `GET /auth/me` e `POST /auth/bootstrap-admin`
  - RBAC por role + escopo nas rotas operacionais
  - rotas `/admin/**` protegidas exclusivamente por JWT `ADMIN`

---

## 6. Estratégia operacional (homolog/dev)

Como foi decidido resetar para começar correto:

1. Subir nova versão com schema de identidade.
2. Recriar banco em homolog/dev (volume limpo).
3. Cadastrar `tenant` e `empresa` antes de testar fluxo de ingestão.
4. Só então criar ApiKey de agent e executar pipeline.

Observação:
- produção terá plano separado (sem reset cego), com migração assistida.

---

## 7. Critério de aceite

- Não é possível criar ApiKey sem `tenant`/`empresa` existente e ativa.
- Não é possível criar importação para `tenant`/`empresa` inexistente/inativa.
- Consultas fiscais retornam dados somente de empresa válida.
- Todas as alterações via Flyway incremental e `ddl-auto=validate`.
- Testes automatizados verdes (`mvn test`) cobrindo regras de integridade.
