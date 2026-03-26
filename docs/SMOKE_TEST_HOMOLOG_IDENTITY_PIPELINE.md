# Smoke Test Guiado — Identidade + Agent + Pipeline

Objetivo: validar em homologação/local que o fluxo completo está operacional após as fases de identidade.

Este roteiro usa como base o arquivo `docs/FiscalAnalyzer.routes.http`.

---

## 1) Pré-requisitos

1. API no ar (`/actuator/health` retornando `UP`).
2. Banco e filas acessíveis.
3. `docs/FiscalAnalyzer.routes.http` com:
   - `@baseUrl`
   - `@userJwt` (obter via `POST /auth/login`)
   - variáveis de tenant/empresa/user ajustadas.
4. Para ambiente novo (sem usuários), usar `POST /auth/bootstrap-admin` com `X-Bootstrap-Token` antes do login.

---

## 2) Sequência recomendada (na ordem)

### Etapa A — Identidade (admin)

Execute no `docs/FiscalAnalyzer.routes.http`:

0. (Somente ambiente novo) `Auth - bootstrap inicial`
1. `Auth - login usuário local (JWT)`

2. `Admin - criar tenant`
3. `Admin - listar tenants`
4. `Admin - criar empresa dentro do tenant`
5. `Admin - listar empresas do tenant`
6. `Admin - criar usuário local`
7. `Admin - adicionar role ao usuário`
8. `Admin - vincular usuário à empresa`

Critério de aceite:
- tenant/empresa/usuário criados com status `ATIVO/ATIVA`.
- usuário retorna com role e empresa vinculada.

### Etapa B — Segurança Agent

Execute:

1. `Admin - criar Agent API Key`
2. `Agent Session`
3. `Agent Upload URL`

Critério de aceite:
- criação de chave retorna `apiKey` plaintext (apenas nessa resposta).
- `agent/session` retorna sucesso com `tenantId/empresaId`.
- `agent/upload-url` retorna URL pré-assinada + `objectKey`.

### Etapa C — Ingestão

Escolha um dos fluxos:

1. ZIP:
   - `Upload ZIP (rota principal)`
2. Manifesto:
   - `Manifest autenticado (contrato do agente)`

Depois execute:

1. `Importação por ID`
2. `Itens da importação (paginado)`
3. `Itens da importação por status`
4. `Documento por chave de acesso`

Critério de aceite:
- `importacao` evolui para status final (`CONCLUIDO`/equivalente no cenário).
- `import_item` sai de pendente para final (`PARSEADO`, `DUPLICADO`, etc.).
- consulta de documento retorna payload fiscal mínimo esperado.

---

## 3) Validação de banco (SQL)

```sql
-- Tenant/empresa
select id, nome, status, created_at from tenant order by id desc limit 10;
select id, tenant_id, cnpj, razao_social, status, created_at from empresa order by id desc limit 10;

-- Usuario local + vínculos
select id, nome, email, status, created_at from app_user order by id desc limit 10;
select app_user_id, app_role_id, created_at from app_user_role order by created_at desc limit 20;
select app_user_id, tenant_id, empresa_id, created_at from app_user_empresa order by created_at desc limit 20;

-- Chaves do agente
select id, tenant_id, empresa_id, key_prefix, ativo, criado_at, revogado_at
from agent_api_key
order by id desc
limit 20;

-- Pipeline
select id, tenant_id, empresa_id, status, source_type, total_encontrado, total_processado, total_erros, erro_codigo
from importacao
order by id desc
limit 20;

select id, importacao_id, status, xml_path, storage_object_key, access_key, model, issue_date, erro_codigo
from import_item
order by id desc
limit 50;

select id, tenant_id, empresa_id, model, access_key, issue_date, emit_cnpj, dest_cnpj, total_amount
from fiscal_document
order by id desc
limit 20;

select id, tenant_id, empresa_id, access_key, fiscal_document_id
from fiscal_document_registry
order by id desc
limit 20;
```

---

## 4) Casos negativos mínimos (sanidade)

1. Tentar criar `Agent API Key` para tenant/empresa inexistente:
   - esperado: `422 UNPROCESSABLE_ENTITY`.
2. Tentar upload/manifesto para empresa inativa:
   - esperado: `403 AUTH_FORBIDDEN`.
3. Manifesto com `objectKey` inexistente:
   - esperado: `422 UNPROCESSABLE_ENTITY`.
4. JWT ausente em rota `/admin/**`:
   - esperado: `401` ou `403` conforme cenário.

---

## 5) Critério final para liberar agent/front

Liberar somente se:

1. endpoints administrativos de identidade funcionando (CRUD + vínculos),
2. criação e uso de Agent API Key funcionando,
3. pipeline completo (upload/manifesto -> parse -> consulta) funcionando,
4. testes automatizados verdes (`./mvnw -q test`),
5. sem mensagens presas inesperadamente em DLQ.
