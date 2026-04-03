# FiscalAnalyzer — Architecture Reference

Este documento descreve a arquitetura técnica do FiscalAnalyzer.
Ele explica os fluxos, responsabilidades e decisões arquiteturais,
servindo como guia para desenvolvimento humano e ferramentas de IA.

---

## 1. Visão geral

O FiscalAnalyzer é uma aplicação backend desenvolvida em **Java 21 + Spring Boot**,
projetada para ingestão e análise de grandes volumes de documentos fiscais
(NF-e modelo 55 e NFC-e modelo 65).

O sistema suporta dois modos de entrada:
- upload via API (ZIP)
- ingestão em alto volume via **agente/desktop + storage + manifesto**

A arquitetura é orientada a:
- processamento assíncrono
- alto volume de dados
- parsing eficiente de XML
- separação clara de responsabilidades

O sistema é **container-first**, executado via Docker em ambientes locais e produtivos.

---

## 2. Componentes principais

### 2.1 API (Camada Web)
Responsável por:
- receber uploads de arquivos `.zip`
- registrar importações
- registrar lotes por manifesto (alto volume)
- expor endpoints de consulta e monitoramento
- nunca processar XML diretamente

Tecnologias:
- Spring Web (REST)
- Bean Validation

---

### 2.2 Fila de Mensagens
Usada para:
- orquestrar etapas da importação
- desacoplar upload de processamento
- permitir paralelismo controlado
- suportar retry e tolerância a falhas

Tecnologia:
- RabbitMQ

---

### 2.3 Workers de Processamento
Executam:
- extração do ZIP
- identificação de XMLs
- parsing streaming
- persistência no banco

Características:
- não expostos via HTTP
- orientados a batch
- executam em paralelo conforme configuração

---

### 2.4 Parser XML
O parser é o núcleo do sistema.

Princípios:
- leitura **streaming** (StAX/SAX)
- baixo consumo de memória
- parser de documento (NF-e/NFC-e) e parser de evento (procEventoNFe/evento)
- diferenciação por `ide.mod` (55 ou 65)

Responsabilidades:
- ler XML
- validar estrutura mínima
- mapear para modelos canônicos internos
- classificar documento vs evento fiscal sem carregar XML inteiro
- nunca acessar banco ou fila

---

### 2.5 Persistência
Armazena:
- fatos fiscais (documentos e itens)
- controle de importação

Tecnologia:
- PostgreSQL
- Spring Data JPA
- Flyway (migrations)

Estratégias:
- particionamento por data (`issue_date`)
- índices focados em análise fiscal
- idempotência por chave da nota
- diferenciação de origem por `importacao.source_type` (`ZIP`/`MANIFEST`)

---

### 2.6 Armazenamento de Arquivos
Arquivos originais são mantidos para:
- auditoria
- reprocessamento
- rastreabilidade

Tecnologia:
- MinIO/S3 (compatível com provedores S3, ex.: Backblaze B2)

---

### 2.7 Agente de Ingestão (alto volume)
Responsável por:
- ler arquivos XML no ambiente do cliente
- enviar XMLs para storage com upload concorrente e retomável
- gerar manifesto (`object_key`, hash, tamanho, tenant/empresa)
- solicitar criação de lote para processamento assíncrono

Características:
- não parseia regra fiscal
- não persiste no banco do domínio
- não publica direto no RabbitMQ (sempre via API do backend)

---

### 2.8 Segurança Agent ↔ Backend
Responsável por:
- autenticar agente via `ApiKey` (`Bearer`)
- autorizar por escopo `tenant/empresa`
- emitir URL pré-assinada para upload (`/agent/upload-url`)
- auditar emissões de upload URL (`agent_upload_audit`)
- auditar autenticação/autorização (`agent_auth_audit`)
- aplicar rate-limit por `apiKey` e lockout para tentativas inválidas

Características:
- chave em plaintext exibida apenas na criação
- backend persiste apenas hash SHA-256 da chave
- manifesto validado contra `tenantId/empresaId` da chave
- respostas de proteção retornam `429 RATE_LIMITED` com header `Retry-After`
- estado de rate-limit/lockout persistido no Redis para consistência entre réplicas
- rotação de chave sem downtime via endpoint admin de `rotate`
- retenção automática de auditoria (`agent_auth_audit` e `agent_upload_audit`) por job agendado

---

### 2.9 Presença Operacional de Agent
Responsável por:
- registrar presença por instância (`tenant/empresa/agentId`)
- receber heartbeat periódico do agente
- expor leitura para monitoramento no front admin

Características:
- read model dedicado em `agent_instance_status`
- status operacional: `ONLINE`, `DEGRADED`, `OFFLINE` (com janelas configuráveis)
- atualização por `POST /agent/session` + `POST /agent/heartbeat`
- refresh automático de status stale via scheduler

---

### 2.10 Identidade Interna (Tenant/Empresa)
Responsável por:
- manter cadastro canônico de `tenant` e `empresa`
- validar escopo ativo/inativo antes de upload/manifesto/consulta/doc e gestão de ApiKey
- servir de base para RBAC de usuários locais nas próximas fases

Status atual:
- schema base implementado via migration `V17`
- validação de tenant/empresa ativa aplicada nas rotas críticas do core
- FKs compostas em tabelas de domínio aplicadas via `V18` (com estratégia de transição para legado)
- endpoints administrativos disponíveis para cadastro de tenant/empresa/usuário local e vínculos de acesso

---

## 3. Fluxo principal de importação

### 3.1 Upload
1. Usuário envia um `.zip` via API
2. API valida tipo e tamanho
3. Arquivo é salvo no storage
4. Registro de `importacao` é criado
5. Mensagem é publicada na fila

---

### 3.2 Extração
1. Worker consome mensagem de extração
2. ZIP é lido em streaming
3. XMLs são listados e validados
4. Um `import_item` é criado por XML
5. Mensagens de parsing são enfileiradas **após COMMIT** da transação de extração
   (via evento + `@TransactionalEventListener`), evitando corrida com o parser

---

### 3.3 Parsing
1. Worker consome mensagem de parsing
2. XML é lido em streaming
3. Tipo de XML é identificado:
   - documento fiscal (`NFe`/`nfeProc`)
   - evento fiscal (`evento`/`procEventoNFe`)
4. Se documento: modelo (55/65), cabeçalho e itens são mapeados e persistidos
5. Se evento: dados do evento são persistidos e vinculados ao documento por `access_key`
6. Regras de efeito são aplicadas (ex.: cancelamento `110111` aceito marca documento como `CANCELADA`)
7. Status do item é atualizado

---

### 3.4 Finalização
Quando todos os itens de uma importação são processados:
- a importação é marcada como concluída
- erros ficam disponíveis para análise
- relatórios podem ser gerados

---

### 3.5 Fluxo de alto volume (agente + manifesto)
1. Agente envia XMLs para storage (chaves determinísticas)
2. Agente gera manifesto do lote
3. API registra importação a partir do manifesto
4. `import_item` é criado em batch e mensagens de parse são enfileiradas
5. Worker de parsing lê **XML direto no storage** (sem releitura de ZIP)
6. Documento/itens são persistidos com idempotência por chave fiscal
7. Lote é finalizado quando não restarem itens pendentes

---

### 3.6 Fluxo de cobertura e backfill (Fase 3)
1. API consulta cobertura agregada por tenant/empresa (`GET /documents/coverage`)
2. Operação pode disparar backfill controlado (`POST /documents/coverage/backfill`)
3. Serviço seleciona itens elegíveis (`PARSEADO`/`DUPLICADO`)
4. XML é relido por origem:
   - direto no storage (`storage_object_key`)
   - ou ZIP legado (`arquivo_path` + `xml_path`)
5. Campos/tabelas da cobertura fiscal são atualizados no `fiscal_document` e filhas
6. Métricas de processado/atualizado/pulado/falha são publicadas

---

## 4. Estrutura de pacotes (alto nível)

A estrutura do código segue o domínio do problema:

br.com.techbr.fiscalanalyzer
├── config # Configurações Spring, fila, storage, datasource
├── agent # Endpoints/segurança/auditoria para agente de ingestão
├── common # Enums, value objects, exceções comuns
├── identity # Cadastro interno de tenant/empresa/usuário local
├── importacao # Upload e controle de importações
├── xml # Parser e modelos canônicos de XML
├── documento # Persistência de documentos fiscais
├── item # Persistência de itens fiscais
├── queue # Mensagens, producers e consumers
├── storage # Integração com MinIO/S3


Cada pacote possui responsabilidade única e clara.

---

## 5. Convenções importantes

### 5.1 Separação de responsabilidades
- Controller **não** faz parsing
- Parser **não** acessa banco
- Worker **não** retorna HTTP
- Serviço de domínio **não** conhece fila

---

### 5.2 Nomenclatura
- Entidades de banco usam nomes do domínio fiscal
- DTOs de parser usam prefixo `Parsed`
- Mensagens de fila usam sufixo `Message`

---

### 5.3 Tratamento de erros
- Erros de parsing não derrubam a importação inteira
- Cada XML possui status individual
- Mensagens de erro são registradas no `import_item`

### 5.4 Fluxo de DLQ (retry esgotado)

```
Consumer principal → retry exponencial (até 5x, máx 30s) → QueueRetryRecoverer
    → AmqpRejectAndDontRequeueException → RabbitMQ move para DLQ
    → DLQ Consumer (sem retry) → DlqHandlerService
        ├── Parse DLQ  → import_item.status = FALHA_PERMANENTE
        └── Extract DLQ → importacao.status = FALHA
```

Status `FALHA_PERMANENTE` é terminal — não será reprocessado automaticamente.
Itens nesse status não bloqueiam a finalização da `importacao`.

Métricas associadas:
- `queue.retry{queue=...}` — incrementado a cada tentativa
- `queue.dlq{queue=...}` — incrementado quando mensagem vai para DLQ

---

## 6. Escalabilidade e futuro

A arquitetura foi desenhada para permitir:
- aumento de workers horizontalmente
- inclusão futura de SPED
- inclusão de novos modelos fiscais
- separação em microserviços, se necessário
- ingestão contínua de milhões de XML via agente local

Nenhuma decisão atual bloqueia essas evoluções.

---

## 7. O que NÃO fazer

- Não processar XML em endpoints HTTP
- Não carregar XML inteiro em memória
- Não misturar regra fiscal com persistência
- Não persistir resultado fiscal na tabela de fatos
- Não enviar payload de XML em mensagens RabbitMQ
- Não depender de varredura massiva (`ListObjects`) para descobrir arquivos; usar manifesto

---

## 8. Roadmap por fases (status atual)

Objetivo desta seção: deixar explícito em que etapa do produto estamos, para manter continuidade quando o contexto for compactado.

Fases:
- Fase 0 — Infra base (Docker + Flyway + `ddl-auto=validate`) — **CONCLUÍDA**
- Fase 1 — Upload + Storage + publicação de `ExtractZipMessage` — **CONCLUÍDA**
- Fase 2 — Worker de extração (ZIP streaming, `import_item`, idempotência) — **CONCLUÍDA**
- Fase 3 — Worker de parsing (streaming XML, `fiscal_document`, registry, AFTER_COMMIT) — **CONCLUÍDA**
- Fase 4 — Read Model e endpoints de consulta (`/imports`, `/imports/{id}/items`, `/documents/{accessKey}`) — **CONCLUÍDA**
- Fase 5 — Hardening para alto volume (1M+ XML): ingestão por agente/manifests, tuning de concorrência, custo/throughput e operação — **CONCLUÍDA (BACKEND CORE)**
- Fase 6 — Fundação de identidade (`tenant`, `empresa`, `usuário` local, FKs compostas, APIs admin) — **CONCLUÍDA**
- Fase 7 — Autenticação de usuário (JWT/RBAC) + consolidação operacional para front/agent — **EM ANDAMENTO**

Status atual:
- Estamos em **Fase 7**.
- Não avançar para novas funcionalidades fiscais antes de concluir o pacote de operação/admin (agent + front + auth de usuário).
- Baseline local já medido no pipeline atual (arquivo com 50 XML): ver `INGESTION_HIGH_VOLUME.md`.
- Tuning inicial de concorrência/prefetch do Rabbit já aplicado; manter ajustes guiados por medição.
- DLQ consumers implementados (`ParseDlqConsumer`, `ExtractDlqConsumer`) via `DlqHandlerService`.
- Agente de ingestão (C#) documentado em `AGENT_ARCHITECTURE.md` — integração backend pronta; implementação evolui no projeto do agente.
- Endpoint `POST /imports/manifest` implementado (cria `importacao` MANIFEST + `import_item` em batch e publica parse AFTER_COMMIT).
- Worker de parse suporta dois modos: ZIP (`zipEntryName`) e XML direto no storage (`storage_object_key`).
- Segurança backend↔agent (Fases 1-3) implementada: `POST /agent/session`, `POST /agent/upload-url`, `POST /imports/manifest` protegido com Bearer ApiKey + cross-check tenant/empresa.
- Gestão de ApiKey ativa via rotas admin (`/admin/empresas/{empresaId}/agent-keys`, incluindo `rotate`), protegidas por JWT `ADMIN`.
- APIs administrativas de identidade ativas: `tenant`, `empresa`, `usuário` local e vínculos de acesso.
- Fase 7.1 avançada: autenticação local de usuário disponível em `POST /auth/login` com JWT e endpoint `GET /auth/me`.
- Bootstrap inicial disponível em `POST /auth/bootstrap-admin` (protegido por `X-Bootstrap-Token`) para ambiente sem usuários.
- Rotas `/admin/**` exigem JWT de usuário com role `ADMIN` (sem fallback legado).
- Fase 7.2 iniciada: RBAC mínimo ativo por role + escopo de empresa nas rotas de operação:
  - escrita: `POST /imports/upload` (roles `ADMIN`/`OPERADOR`)
  - escrita operacional: `POST /imports/{id}/reprocess` (roles `ADMIN`/`OPERADOR`)
  - leitura: `GET /imports/{id}`, `GET /imports/{id}/items`, `GET /imports/{id}/failures-summary`, `GET /documents/{accessKey}` (roles `ADMIN`/`OPERADOR`/`LEITOR`)
  - usuários não-admin precisam de vínculo explícito em `app_user_empresa` para `tenantId/empresaId`.
- Hardening operacional ativo: Redis para rate-limit/lockout distribuído, auditoria dedicada e retenção automática dos eventos de segurança.
- Decisão arquitetural de identidade aprovada em `TENANT_EMPRESA_USUARIO_DECISION.md`; implementação guiada por `IDENTITY_FOUNDATION_CHECKLIST.md`.

---

## 9. Como usar este documento com IA (Codex)

Antes de implementar qualquer código:
1. Ler `DOCS_INDEX.md` (mapa oficial da documentação)
2. Ler este arquivo (`README_ARCHITECTURE.md`)
3. Ler `DOMAIN.md` e `DATABASE.md`
4. Se houver impacto no agente, ler/atualizar `AGENT_INTEGRATION_CONTRACT.md`
5. Se houver impacto em fase futura (agent/front), alinhar com `NEXT_STEPS_AGENT_FRONT.md`
6. Implementar apenas o escopo solicitado
7. Respeitar responsabilidades dos pacotes
8. Para chamadas manuais de API, usar `FiscalAnalyzer.routes.http`
9. Para deploy em Dockploy, seguir `DEPLOY_DOCKPLOY.md`
10. Para segurança backend↔agent, acompanhar `BACKEND_AGENT_SECURITY_PLAN.md` e `BACKEND_AGENT_SECURITY_CHECKLIST.md`
11. Para decisão de cadastro mestre (`tenant/empresa/usuário`), acompanhar `TENANT_EMPRESA_USUARIO_DECISION.md`
12. Para execução por fases da fundação de identidade, acompanhar `IDENTITY_FOUNDATION_CHECKLIST.md`
13. Para validação manual fim-a-fim (identidade + agent + pipeline), seguir `SMOKE_TEST_HOMOLOG_IDENTITY_PIPELINE.md`
14. Para handoff direto às equipes Agent e Front, usar `HANDOFF_AGENT_FRONT.md`
15. Para iniciar o front administrativo do zero, seguir `FRONTEND_SPEC.md`
16. Para homologação da fase de monitoramento do front, seguir `FASE6_HOMOLOG_CHECKLIST.md`

Isso garante consistência e evita código fora do padrão arquitetural.
