# FiscalAnalyzer — Contrato API do Módulo de Documentos (Front Viewer)

Objetivo: formalizar o contrato backend para o front de visualização avançada de documentos fiscais.

Status deste documento: **contrato ativo (P0 + P1 completos + P2 parcial)**.

Data de referência: **2026-04-04**

---

## 0. Status de Implementação (backend atual)

- Implementado:
  - `GET /documents` (filtros + paginação + sort controlado)
  - `GET /documents/{accessKey}` (detalhe enriquecido com itens)
  - `GET /documents/{accessKey}/xml` (download XML original para importação MANIFEST e ZIP)
  - `GET /documents/{accessKey}/events`
  - `GET /documents/summary`
  - `POST /documents/export` (CSV/XLSX/PDF)
  - `GET /documents/emitters`
  - `GET /documents/receivers`
  - `GET /documents/stats/cfop`
  - `GET /documents/stats/ncm`

---

## 1. Princípios

- Todas as rotas exigem `Authorization: Bearer <JWT>`.
- Escopo obrigatório por `tenantId` e `empresaId` (query params).
- Paginação padrão: `page=0`, `size=20`, limite máximo recomendado `size=200`.
- Ordenação padrão para listagem: `issueDate,desc`.
- Backend responde erros no padrão já vigente:
  - `400 VALIDATION_ERROR`
  - `401 AUTH_UNAUTHORIZED`
  - `403 AUTH_FORBIDDEN`
  - `404`/`400` conforme recurso
  - `500 INTERNAL_ERROR` / `INFRA_ERROR`

---

## 2. P0 — Crítico

### 2.1 GET `/documents`

Listagem paginada de documentos fiscais.

### Query params

- `tenantId` (obrigatório)
- `empresaId` (obrigatório)
- `model` (`55` ou `65`, opcional)
- `operationType` (`E` ou `S`, opcional)
- `statusDocumento` (`ATIVA` ou `CANCELADA`, opcional)
- `emitCnpj` (opcional)
- `destCnpj` (opcional)
- `issueDateFrom` (`YYYY-MM-DD`, opcional)
- `issueDateTo` (`YYYY-MM-DD`, opcional)
- `importacaoId` (opcional)
- `page` (opcional)
- `size` (opcional)
- `sort` (opcional, ex.: `issueDate,desc`)

### Sort permitido (inicial)

- `issueDate`
- `totalAmount`
- `createdAt`

### Response 200 (Page)

```json
{
  "content": [
    {
      "accessKey": "27250829422082000144653010001838209805114679",
      "model": 65,
      "nfeSerie": "001",
      "nfeNumero": 1234,
      "issueDate": "2026-03-01",
      "operationType": "S",
      "statusDocumento": "ATIVA",
      "emitCnpj": "29422082000144",
      "emitRazaoSocial": "EMPRESA EMITENTE LTDA",
      "destCnpj": "12345678000190",
      "destRazaoSocial": "EMPRESA DESTINO LTDA",
      "totalAmount": 1500.00,
      "importacaoId": 1
    }
  ],
  "totalElements": 350,
  "totalPages": 18
}
```

---

### 2.2 GET `/documents/{accessKey}`

Detalhe completo do documento por chave.

### Query params

- `tenantId` (obrigatório)
- `empresaId` (obrigatório)

### Response 200 (Detalhe)

```json
{
  "model": 55,
  "accessKey": "35191111111111111111550010000000011000000010",
  "issueDate": "2026-03-01",
  "operationType": "S",
  "statusDocumento": "ATIVA",
  "emitCnpj": "11111111000111",
  "emitRazaoSocial": "EMPRESA EMITENTE LTDA",
  "emitUf": "SP",
  "emitMunicipio": "SAO PAULO",
  "destCnpj": "22222222000122",
  "destRazaoSocial": "EMPRESA DESTINO LTDA",
  "nfeNumero": 1234,
  "nfeSerie": "001",
  "naturezaOperacao": "VENDA DE MERCADORIA",
  "totalAmount": 1500.00,
  "icmsTotal": 180.00,
  "pisTotal": 10.50,
  "cofinsTotal": 48.00,
  "ipiTotal": 0.00,
  "issTotal": null,
  "importacaoId": 1,
  "xmlPath": "1/2/abc.xml",
  "xmlHash": "abc123...",
  "items": [
    {
      "nItem": 1,
      "descricao": "PRODUTO X",
      "ncm": "84713012",
      "cfop": "5102",
      "unidade": "UN",
      "quantidade": 10,
      "valorUnitario": 150.00,
      "valorTotal": 1500.00,
      "icmsAliq": 12.0,
      "icmsValor": 180.0
    }
  ]
}
```

---

### 2.3 GET `/documents/{accessKey}/xml`

Download do XML original para auditoria.

### Query params

- `tenantId` (obrigatório)
- `empresaId` (obrigatório)

### Response 200

- `Content-Type: application/xml`
- `Content-Disposition: attachment; filename="{accessKey}.xml"`
- body: XML bruto

---

## 3. P1 — Alta prioridade

### 3.1 GET `/documents/summary`

Resumo do período para dashboard.

### Query params

- `tenantId` (obrigatório)
- `empresaId` (obrigatório)
- `issueDateFrom` (obrigatório)
- `issueDateTo` (obrigatório)

### Response 200

```json
{
  "totalDocumentos": 350,
  "totalNFe": 310,
  "totalNFCe": 40,
  "totalCanceladas": 12,
  "valorTotalEntradas": 85000.00,
  "valorTotalSaidas": 220000.00,
  "icmsTotalSaidas": 26400.00,
  "periodoInicio": "2026-03-01",
  "periodoFim": "2026-03-31"
}
```

---

### 3.2 POST `/documents/export`

Exportação de listagem (CSV/XLSX/PDF).

### Request body (alvo)

```json
{
  "tenantId": 1,
  "empresaId": 2,
  "model": 55,
  "operationType": "S",
  "emitCnpj": "11111111000111",
  "destCnpj": "22222222000122",
  "issueDateFrom": "2026-03-01",
  "issueDateTo": "2026-03-31",
  "format": "PDF"
}
```

### Response 200

- `Content-Type`: conforme formato (`text/csv`, XLSX ou `application/pdf`)
- `Content-Disposition: attachment; filename="documents-2026-03.csv"`

Obs.: para volume muito alto, backend pode evoluir para modo assíncrono (job + download posterior).

---

### 3.3 GET `/documents/{accessKey}/events`

Eventos vinculados ao documento (cancelamento, CCe, inutilização).

### Query params

- `tenantId` (obrigatório)
- `empresaId` (obrigatório)

### Response 200

```json
[
  {
    "tipo": "CANCELAMENTO",
    "descricao": "Cancelamento de NF-e",
    "dataEvento": "2026-03-05T14:30:00Z",
    "protocolo": "227250213121229"
  }
]
```

---

## 4. P2 — Evolução

### 4.1 GET `/documents/emitters`

- params: `tenantId`, `empresaId`, `q`
- response:

```json
[
  {
    "cnpj": "11111111000111",
    "razaoSocial": "EMPRESA EMITENTE LTDA"
  }
]
```

### 4.2 GET `/documents/receivers`

- params: `tenantId`, `empresaId`, `q`
- response igual a emitters.

### 4.3 GET `/documents/stats/cfop`

- params: `tenantId`, `empresaId`, `issueDateFrom`, `issueDateTo`

### 4.4 GET `/documents/stats/ncm`

- params: `tenantId`, `empresaId`, `issueDateFrom`, `issueDateTo`

---

## 5. Sequência de implementação recomendada

1. P0 completo (`GET /documents`, detalhe enriquecido, download XML).
2. P1 (`summary`, `events`, export).
3. P2 (autocomplete e estatísticas fiscais).

---

## 6. Critérios de aceite para o front

- tela de listagem funciona apenas com `GET /documents` (sem workaround local);
- tela de detalhe não precisa de múltiplas chamadas paralelas para montar dados básicos;
- export entrega arquivo com filtros aplicados;
- nota cancelada aparece coerente entre `statusDocumento` e eventos.
