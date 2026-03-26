# FiscalAnalyzer Frontend — Especificação Técnica

Data: 2026-03-25
Status: **Documento de referência alinhado com o backend atual**
Backend homologação: `https://api.fiscalanalyzer.techbrsolucoes.com.br`

---

## 1. Decisão de stack

### Framework: Vite + React 19 + TypeScript

**Motivo da escolha em vez de Next.js:**

Este é um painel administrativo autenticado — não há SEO, não há páginas públicas,
não há geração estática. Todos os requisitos do projeto são:

- autenticação por JWT
- formulários e tabelas com estado pesado no cliente
- upload de arquivo
- polling de status de importação

O App Router do Next.js introduz boundary server/client que precisa ser gerenciado
em cada componente. Para um admin dashboard isso é overhead sem ganho real.

Com Vite + React, todo o código é cliente. O modelo mental é uniforme, o build
gera estática pura, o deploy pode ser qualquer CDN ou servidor Nginx — mais simples
e mais fácil de manter.

### Stack completa

| Camada | Tecnologia | Versão alvo | Motivo |
|---|---|---|---|
| Framework | React | 19 | hooks, concurrent features |
| Build | Vite | 6 | dev server rápido, build otimizado |
| Linguagem | TypeScript | 5.x | tipagem de contratos da API |
| Roteamento | React Router | v7 | roteamento declarativo, lazy routes |
| Estado global | Zustand | 5 | simples, sem boilerplate, persistência fácil |
| Data fetching | TanStack Query | v5 | cache, invalidação, paginação, loading states |
| Forms | React Hook Form + Zod | — | validação tipada, performance |
| UI | shadcn/ui + Tailwind CSS | — | componentes copiáveis, sem lock-in de lib |
| HTTP | Axios | — | interceptors de JWT e erro global |
| Testes | Vitest + Testing Library | — | integrado com Vite, sem Jest config |
| Mock de API | MSW | v2 | intercepta fetch/axios em testes |

---

## 2. Decisões de segurança

### 2.1 Armazenamento do JWT

Token armazenado em **memória (Zustand store)** com fallback em `sessionStorage`.

- `localStorage` é descartado por aumentar persistência do token entre sessões.
- `httpOnly cookie` exigiria backend/proxy BFF para emissão e refresh de sessão.
- `sessionStorage` **não elimina risco de XSS** (também é acessível por JS),
  mas reduz persistência entre abas/sessões e atende o cenário atual.

Fluxo:
1. Login → token retornado pela API
2. Token salvo no Zustand store (memória) + `sessionStorage` como backup
3. Ao recarregar a página: store hidrata via `sessionStorage`
4. Logout: limpa store + limpa `sessionStorage`
5. Token expirado (interceptor 401): limpa tudo e redireciona para `/login`

Hardening obrigatório:
- CSP restritivo em produção.
- Proibir `dangerouslySetInnerHTML` e scripts inline no front.
- Nunca logar JWT em console/network logger customizado.

### 2.2 Proteção de rotas

Componente `<RequireAuth>` envolve todas as rotas autenticadas.
Componente `<RequireRole roles={['ADMIN']}>` envolve rotas exclusivas de admin.

Não há flash de tela — verificação síncrona via store hidratado antes do render.

### 2.3 Exibição de Agent API Key

Regra: a chave plaintext é exibida **uma única vez**, imediatamente após a criação.
O backend só armazena o hash SHA-256.

UX obrigatória:
- Modal com a chave em campo `readonly` + botão "Copiar"
- Botão "Fechar" **desabilitado** até o checkbox "Confirmo que copiei a chave" ser marcado
- Aviso em destaque: "Esta chave não poderá ser recuperada após fechar esta janela"
- Na tabela: exibir apenas o prefixo (`fa_live_mZ2r...`) — nunca a chave completa

---

## 3. Pré-requisitos de backend

Antes de iniciar as fases dependentes, confirmar os contratos abaixo.

### 3.1 Endpoint de listagem de importações (OBRIGATÓRIO)

**Problema:** não existe `GET /imports` — apenas `GET /imports/{id}`.
Sem listagem, não há painel de monitoramento.

**Contrato solicitado:**

```
GET /imports?tenantId={tenantId}&empresaId={empresaId}&page=0&size=20&status=CONCLUIDO
Authorization: Bearer {jwt}

Roles aceitas: ADMIN, OPERADOR, LEITOR (com escopo de empresa)
```

Resposta esperada (padrão Spring Page):
```json
{
  "content": [
    {
      "id": 1,
      "status": "CONCLUIDO",
      "sourceType": "ZIP",
      "totalItems": 50,
      "processedItems": 48,
      "failedItems": 2,
      "createdAt": "2026-03-25T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

Filtros desejados: `status` (opcional), `page`, `size`, `sort` (opcional).

### 3.2 Payload atual de `GET /auth/me` (JÁ IMPLEMENTADO)

Formato atual do backend:
```json
{
  "user": {
    "id": 1,
    "nome": "Operador Homolog",
    "email": "operador@empresa.com",
    "status": "ATIVO",
    "lastLoginAt": "2026-03-25T14:00:00Z",
    "roles": ["OPERADOR"],
    "empresas": [
      { "tenantId": 99, "empresaId": 99 }
    ]
  }
}
```

Observação:
- hoje não retorna `razaoSocial` no vínculo.
- para UI de seletor amigável, recomenda-se evolução do backend
  para incluir metadados de empresa no `/auth/me` (ou endpoint dedicado).

### 3.3 Contrato de status (backend atual)

`ImportacaoStatus`:
- `RECEBIDO`
- `EM_EXTRACAO`
- `EXTRAIDO`
- `PROCESSANDO`
- `CONCLUIDO`
- `ERRO`
- `FALHA`

`ImportItemStatus`:
- `PENDENTE`
- `PENDENTE_PARSE`
- `PROCESSANDO`
- `PARSEADO`
- `DUPLICADO`
- `FALHA_PARSE`
- `CONCLUIDO`
- `ERRO`
- `FALHA_PERMANENTE`

---

## 4. Contexto de empresa (seletor de contexto)

Usuários `OPERADOR` e `LEITOR` operam dentro do escopo de empresa(s) vinculadas.
O front precisa gerenciar qual empresa está ativa na sessão.

**Fluxo após login:**

1. Chamar `GET /auth/me` e armazenar `roles` e `empresas` no store
2. Se `role = ADMIN`: sem seletor obrigatório (admin pode operar globalmente)
3. Se `role = OPERADOR/LEITOR` com **1 empresa vinculada**: selecionar automaticamente
4. Se `role = OPERADOR/LEITOR` com **N empresas vinculadas**: exibir tela de seleção de contexto antes do dashboard

**Store de contexto:**
```ts
interface EmpresaContext {
  tenantId: number
  empresaId: number
  displayName?: string
}
```

O `tenantId` e `empresaId` do contexto ativo são injetados automaticamente
nas requisições que exigem esses parâmetros.

---

## 5. Estrutura de projeto

```
fiscalanalyzer-front/
├── public/
├── src/
│   ├── api/
│   │   ├── http.ts           # instância Axios + interceptors
│   │   ├── auth.ts           # chamadas de /auth
│   │   ├── tenants.ts        # chamadas de /admin/tenants
│   │   ├── empresas.ts       # chamadas de /admin/tenants/{id}/empresas
│   │   ├── users.ts          # chamadas de /admin/users
│   │   ├── agentKeys.ts      # chamadas de /admin/empresas/{id}/agent-keys
│   │   ├── imports.ts        # chamadas de /imports
│   │   └── documents.ts      # chamadas de /documents
│   ├── components/
│   │   ├── ui/               # componentes shadcn/ui (não editar)
│   │   ├── layout/           # Sidebar, Header, AppShell
│   │   ├── auth/             # RequireAuth, RequireRole
│   │   └── shared/           # StatusBadge, ConfirmDialog, CopyField, etc.
│   ├── pages/
│   │   ├── login/
│   │   ├── dashboard/
│   │   ├── admin/
│   │   │   ├── tenants/
│   │   │   ├── empresas/
│   │   │   ├── users/
│   │   │   └── agent-keys/
│   │   ├── imports/
│   │   └── documents/
│   ├── stores/
│   │   ├── authStore.ts      # token, user, roles
│   │   └── contextStore.ts   # empresaContext ativo
│   ├── hooks/
│   │   └── useEmpresaContext.ts
│   ├── lib/
│   │   ├── constants.ts      # roles, status, etc.
│   │   ├── formatters.ts     # CNPJ, data, moeda
│   │   └── errors.ts         # mapeamento de erros HTTP → mensagem
│   ├── types/
│   │   └── api.ts            # tipos dos contratos da API
│   ├── router.tsx
│   └── main.tsx
├── .env.example
├── .env.local              # não commitado
└── vite.config.ts
```

---

## 6. Convenções de desenvolvimento

### 6.1 Tipagem da API

Todos os contratos da API devem ser tipados em `src/types/api.ts`.
Nenhuma chamada de API retorna `any`.

```ts
// exemplo
export interface ImportacaoSummary {
  id: number
  status: ImportacaoStatus
  sourceType: 'ZIP' | 'MANIFEST'
  totalItems: number
  processedItems: number
  failedItems: number
  createdAt: string
}

export type ImportacaoStatus =
  | 'RECEBIDO'
  | 'EM_EXTRACAO'
  | 'EXTRAIDO'
  | 'PROCESSANDO'
  | 'CONCLUIDO'
  | 'ERRO'
  | 'FALHA'
```

### 6.2 Chamadas de API

Cada módulo em `src/api/` exporta funções puras que retornam Promises.
TanStack Query consome essas funções — sem lógica de fetch dentro de componentes.

```ts
// src/api/imports.ts
export const getImportacao = (id: number): Promise<ImportacaoDetail> =>
  http.get(`/imports/${id}`).then(r => r.data)

export const listImportacoes = (params: ListImportacoesParams): Promise<Page<ImportacaoSummary>> =>
  http.get('/imports', { params }).then(r => r.data)
```

### 6.3 Tratamento de erros

Interceptor global no Axios mapeia erros HTTP para mensagens amigáveis:

| HTTP | Mensagem padrão |
|---|---|
| 401 | Sessão expirada. Faça login novamente. |
| 403 | Sem permissão ou empresa inativa. |
| 404 | Recurso não encontrado. |
| 409 | Conflito — registro já existe. |
| 422 | Dados inválidos. Verifique os campos. |
| 429 | Muitas requisições. Aguarde e tente novamente. |
| 500 | Erro interno. Entre em contato com o suporte. |

Erros são exibidos via `toast` global (shadcn/ui Sonner).
Nenhum componente lida com erros individualmente — exceção: erros de validação de campo (React Hook Form).

### 6.4 Formatação de dados

Usar funções de `src/lib/formatters.ts` — nunca formatar inline em componentes.

```ts
formatCnpj('12345678000199') // → '12.345.678/0001-99'
formatCurrency(1500.5)        // → 'R$ 1.500,50'
formatDate('2026-03-25')      // → '25/03/2026'
```

### 6.5 Variáveis de ambiente

```env
# .env.example
VITE_API_BASE_URL=https://api.fiscalanalyzer.techbrsolucoes.com.br
```

```env
# .env.local (não commitado)
VITE_API_BASE_URL=http://localhost:8080
```

Nunca usar a URL do backend hardcoded no código.

---

## 7. Fases de implementação

### Fase 1 — Fundação e autenticação
**Critério de início:** nenhum
**Critério de aceite:**
- [ ] Projeto buildando sem erros
- [ ] `POST /auth/login` funcional com JWT real
- [ ] Token em `sessionStorage` + Zustand, nunca em `localStorage`
- [ ] `GET /auth/me` hidrata store ao recarregar
- [ ] Interceptor 401 redireciona para `/login` automaticamente
- [ ] Rotas autenticadas protegidas com redirect síncrono (sem flash)
- [ ] Logout limpa store + sessionStorage

**Entregáveis:**
- Setup completo do projeto
- `src/api/http.ts` com interceptors
- `src/stores/authStore.ts`
- Página `/login`
- `<RequireAuth>` e `<RequireRole>`
- Layout base (sidebar + header)
- Dashboard placeholder

---

### Fase 2 — Seletor de contexto de empresa
**Critério de início:** Fase 1 concluída + `GET /auth/me` retornando vínculos
**Critério de aceite:**
- [ ] Após login, ADMIN vai direto ao dashboard
- [ ] OPERADOR/LEITOR com 1 empresa: contexto selecionado automaticamente
- [ ] OPERADOR/LEITOR com N empresas: tela de seleção antes do dashboard
- [ ] Contexto ativo exibido no header (nome da empresa)
- [ ] Troca de empresa disponível via header (dropdown)
- [ ] `tenantId/empresaId` do contexto injetados automaticamente nas requisições

**Entregáveis:**
- `src/stores/contextStore.ts`
- `src/hooks/useEmpresaContext.ts`
- Tela de seleção de empresa
- Integração no header

---

### Fase 3 — Admin: Tenant e Empresa
**Critério de início:** Fase 1 concluída
**Critério de aceite:**
- [ ] CRUD de tenant funcional (criar, listar, filtrar por status, ativar/inativar)
- [ ] CRUD de empresa funcional por tenant (mesmas operações)
- [ ] Rota protegida com `<RequireRole roles={['ADMIN']}>`
- [ ] CNPJ validado no front (14 dígitos numéricos via Zod)
- [ ] Erros 422, 403, 409 exibidos como toast com mensagem específica
- [ ] Confirmação antes de inativar

**Entregáveis:**
- `/admin/tenants` — listagem + criação + detalhe
- `/admin/tenants/{id}/empresas` — listagem + criação + detalhe
- Componentes: `TenantList`, `TenantForm`, `EmpresaList`, `EmpresaForm`, `StatusToggle`

---

### Fase 4 — Admin: Usuários, Roles e Vínculos
**Critério de início:** Fase 3 concluída
**Critério de aceite:**
- [ ] CRUD de usuário funcional
- [ ] Atribuição e remoção de roles funcional (ADMIN/OPERADOR/LEITOR)
- [ ] Vínculo usuário ↔ empresa funcional
- [ ] Alteração de roles reflete na UI sem reload
- [ ] Senha com confirmação na criação

**Entregáveis:**
- `/admin/users` — listagem + criação + detalhe
- `UserRoleManager` — checkboxes por role
- `UserEmpresaBinding` — toggle de vínculo por empresa

---

### Fase 5 — Admin: Agent API Keys
**Critério de início:** Fase 3 concluída
**Critério de aceite:**
- [ ] Criação de chave exibe a chave completa uma única vez no modal
- [ ] Botão "Fechar" bloqueado até checkbox de confirmação de cópia
- [ ] Rotação sem downtime (`revokeOld=false` por padrão)
- [ ] Revogação pede confirmação
- [ ] Tabela exibe apenas o prefixo da chave, nunca o valor completo
- [ ] Chaves revogadas exibem badge "Revogada"

**Entregáveis:**
- `/admin/empresas/{id}/agent-keys` (acessível via detalhe de empresa)
- `AgentKeyCreateModal` com UX de segurança
- `AgentKeyRotateModal`
- `AgentKeyRevokeConfirm`

---

### Fase 6 — Monitoramento de Importações
**Critério de início:** Fase 2 concluída + `GET /imports` disponível no backend
**Critério de aceite:**
- [ ] Listagem de importações paginada, filtrada por status
- [ ] Detalhe da importação com contadores (total / processado / falha)
- [ ] Itens da importação paginados com filtro por status
- [ ] Status dos itens com badge colorido:
  - PARSEADO → verde
  - PENDENTE / PENDENTE_PARSE / PROCESSANDO → cinza/amarelo
  - FALHA_PARSE / FALHA_PERMANENTE / ERRO → vermelho
- [ ] LEITOR não vê botões de ação
- [ ] Acesso restrito por escopo de empresa (tenantId/empresaId do contexto)

**Entregáveis:**
- `/imports` — listagem
- `/imports/{id}` — detalhe + itens
- `ImportStatusBadge`, `ImportItemTable`, `ImportItemStatusFilter`

---

### Fase 7 — Documento Fiscal
**Critério de início:** Fase 6 concluída
**Critério de aceite:**
- [ ] Busca por chave de acesso (44 caracteres) funcional
- [ ] Exibe payload mínimo atual do backend:
  - `model`, `accessKey`, `issueDate`, `operationType`
  - `emitCnpj`, `destCnpj`, `totalAmount`
  - `importacaoId`, `xmlPath`, `xmlHash`
- [ ] Acessível para ADMIN/OPERADOR/LEITOR com escopo correto

**Entregáveis:**
- `/documents/{accessKey}` — detalhe mínimo do documento fiscal
- `FiscalDocumentHeader`, `FiscalDocumentTotals`

Backlog pós-MVP:
- detalhamento de tributos (`ICMS`, `PIS`, `COFINS`) e itens fiscais em endpoint dedicado.

---

### Fase 8 — Upload de ZIP
**Critério de início:** Fase 2 concluída
**Critério de aceite:**
- [ ] Seletor de arquivo aceita apenas `.zip`
- [ ] Seletor de empresa respeita perfil:
  - `ADMIN`: pode escolher tenant/empresa
  - `OPERADOR`: usa empresas do `/auth/me` (com seleção quando houver múltiplas)
- [ ] Feedback de progresso de upload
- [ ] Após sucesso: redireciona para `/imports/{id}`
- [ ] Erros 422/403 exibem mensagem específica (empresa inativa, tenant inexistente)
- [ ] Protegido para ADMIN/OPERADOR

**Entregáveis:**
- `/imports/new` — formulário de upload
- `UploadZipForm`, `UploadProgress`

---

### Fase 9 — Hardening final
**Critério de início:** Fases 1-8 concluídas
**Critério de aceite:**
- [ ] Nenhum token ou chave logado no console
- [ ] Skeleton screens em todas as tabelas durante carregamento
- [ ] Loading spinner em todos os botões de submit
- [ ] Estados vazios com mensagem amigável
- [ ] Testes unitários verdes: `LoginForm`, `RequireAuth`, `AgentKeyCreateModal`, `UploadZipForm`, `StatusBadge`
- [ ] Build sem warnings de TypeScript (`strict: true`)
- [ ] UI funcional em tablets (min 768px)

---

## 8. Sequência e dependências

```
Fase 1 (Fundação)
    │
    ├── Fase 2 (Contexto) ─── depende de /auth/me (já disponível; metadados opcionais)
    │       │
    │       ├── Fase 6 (Importações) ─── depende de backend criar GET /imports
    │       │       └── Fase 7 (Documento Fiscal)
    │       └── Fase 8 (Upload ZIP)
    │
    └── Fase 3 (Tenant/Empresa)
            └── Fase 4 (Usuários)
            └── Fase 5 (Agent Keys)

Fase 9 (Hardening) ─── depois de tudo
```

**Fases que podem ser desenvolvidas em paralelo após Fase 1:**
- Fase 3, 4, 5 (admin de identidade) — não dependem do contexto de empresa
- Fase 2 e 8 — podem ser desenvolvidas juntas

---

## 9. Contratos pendentes com o backend

| # | Item | Tipo | Prioridade | Bloqueia |
|---|---|---|---|---|
| 1 | `GET /imports` com paginação e filtro por status | Novo endpoint | Alta | Fase 6 |
| 2 | `GET /auth/me` com metadados de empresa (`razaoSocial`) | Ajuste de payload | Média | UX da Fase 2/8 |
| 3 | Detalhe fiscal completo (itens + tributos) | Evolução de endpoint | Média | Fase 7 pós-MVP |

Esses pontos devem ser validados antes de iniciar as fases dependentes.

---

## 10. Guardrails

- `X-Admin-Token` foi removido — **não usar**. Todas as rotas `/admin/**` usam JWT.
- Não implementar fluxo de manifesto no front — isso pertence ao agente C#.
- Não hardcodar `tenantId/empresaId` — sempre vir do contexto do store.
- Não logar token, chave de API ou dados fiscais no console.
- Smoke test manual em homologação antes de cada fase ser considerada concluída.
- `tenantId` e `empresaId` são obrigatórios nas rotas que exigem escopo explícito
  (ex.: upload e consulta de documento por chave).
