# Fase 6 — Checklist de Homologação (Monitoramento de Importações)

Data de referência: 2026-03-25  
Objetivo: validar backend + frontend para a fase de monitoramento (`GET /imports` + detalhe + itens).

---

## 1. Banco e migrations

- [ ] Confirmar aplicação das migrations em homolog.
- [ ] Confirmar `V19` aplicada:

```sql
select version, description, success
from flyway_schema_history
where version = '19';
```

---

## 2. Segurança e identidade

- [ ] Login com usuário de teste (`LEITOR` ou `OPERADOR`).
- [ ] Validar `GET /auth/me` com JWT.
- [ ] Confirmar vínculo em `app_user_empresa` para `tenantId/empresaId` usados no teste.

---

## 3. Endpoint novo — `GET /imports`

- [ ] Executar chamada:

```http
GET /imports?tenantId=99&empresaId=99&page=0&size=20&status=CONCLUIDO
Authorization: Bearer <JWT>
```

- [ ] Esperar HTTP `200`.
- [ ] Confirmar payload `content[]` com campos:
  - `id`
  - `status`
  - `sourceType`
  - `totalItems`
  - `processedItems`
  - `failedItems`
  - `createdAt`
  - `updatedAt`
- [ ] Confirmar paginação:
  - `totalElements`
  - `totalPages`
  - `number`
  - `size`

---

## 4. Casos de segurança/validação

- [ ] Sem JWT -> `401`.
- [ ] JWT sem escopo (não-admin fora da empresa) -> `403`.
- [ ] `tenantId/empresaId` inválidos -> `422` (ou erro equivalente padronizado).
- [ ] `sort` inválido (`sort=foo,desc`) -> `400`.

---

## 5. Paginação e ordenação

- [ ] `sort=createdAt,desc` (default) funcionando.
- [ ] `sort=createdAt,asc` funcionando.
- [ ] `page` e `size` refletindo corretamente no resultado.

---

## 6. Integração com tela do front

- [ ] Tela de lista usa `GET /imports` sem workaround.
- [ ] Filtro por status funcionando.
- [ ] Navegação para detalhe funcionando:
  - `GET /imports/{id}`
- [ ] Navegação para itens funcionando:
  - `GET /imports/{id}/items`

---

## 7. Status (contrato oficial para UI)

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

## 8. Regressão mínima

- [ ] `GET /imports/{id}` ok.
- [ ] `GET /imports/{id}/items` ok.
- [ ] `GET /documents/{accessKey}` ok.
- [ ] `POST /imports/upload` mantém regra de role/escopo.

---

## 9. Verificação de desempenho (opcional recomendado)

- [ ] Rodar `EXPLAIN ANALYZE` para listagem principal:

```sql
explain analyze
select *
from importacao
where tenant_id = 99 and empresa_id = 99
order by created_at desc
limit 20 offset 0;
```

- [ ] Confirmar plano sem degradação evidente.

---

## 10. Critério de aceite da Fase 6

- [ ] `GET /imports` estável em homolog.
- [ ] Front consome lista/filtro/paginação sem bloqueio.
- [ ] Segurança e escopo validados.
- [ ] Sem regressão das rotas existentes.
