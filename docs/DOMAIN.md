# FiscalAnalyzer — Domain Reference

Este documento descreve o domínio e os conceitos oficiais do FiscalAnalyzer.
Ele serve como referência para desenvolvimento humano e para ferramentas de IA (ex.: Codex),
garantindo consistência de nomenclatura, fluxo e regras.

---

## 1. Objetivo do produto

O FiscalAnalyzer é uma plataforma web para **ingestão, armazenamento e análise** de documentos fiscais
em formato **XML** (NF-e modelo 55 e NFC-e modelo 65).

O sistema importa arquivos em lote (principalmente via `.zip`), extrai os XMLs, lê em modo streaming,
normaliza os dados e persiste no banco para consultas e cálculos fiscais.

Para alto volume, também suporta ingestão por **agente/desktop** com envio direto de XMLs para storage
e criação de lote por manifesto.

O foco do produto é permitir análises em nível de **item da nota**, pois os impostos e regras fiscais
são predominantemente determinados por CFOP/CST/CSOSN/NCM e bases por item.

---

## 2. Escopo atual

### 2.1 Incluído
- Importação em lote de XMLs de **NF-e (55)** e **NFC-e (65)**.
- Upload de arquivo `.zip` contendo XMLs e subpastas misturadas.
- Ingestão em alto volume via manifesto de objetos no storage.
- Processamento assíncrono via fila (RabbitMQ) com workers.
- Persistência em PostgreSQL:
    - cabeçalho do documento (fiscal_document)
    - itens do documento (fiscal_item)
- Idempotência por chave da nota (evita duplicidade).
- Auditoria mínima: rastrear importação e arquivo origem.
- Cadastro administrativo local de identidade:
    - `tenant`
    - `empresa`
    - `app_user` + vínculos (role/empresa)

### 2.2 Fora do escopo (por enquanto)
- Importação/validação via SPED (EFD Contribuições, ICMS/IPI etc.).
- CT-e e outros modelos.
- Autenticação/Autorização final (RBAC) e multi-tenant avançado (em implementação por fases).
- Cálculo final de “imposto a recuperar” como resultado persistido.
  (Por enquanto, apenas armazenamento de fatos fiscais e consultas.)
- Publicação direta no RabbitMQ por sistemas externos (incluindo agente).

---

## 3. Conceitos do domínio

### 3.1 Documento Fiscal (NF-e / NFC-e)
Um **Documento Fiscal** é um registro fiscal emitido/recebido, representado por XML.
No sistema, chamamos de `fiscal_document`.

Campos essenciais:
- `model`: 55 (NF-e) ou 65 (NFC-e)
- `access_key`: chave de acesso (44 caracteres) — identificador principal
- `issue_date`: data de emissão (base do particionamento)
- `operation_type`: Entrada (E) ou Saída (S)
- `emit_cnpj`: CNPJ do emitente
- `dest_cnpj`: CNPJ do destinatário (pode ser nulo em NFC-e)
- totais: valores do documento (total, ICMS, PIS, COFINS etc.)

### 3.2 Item do Documento
Um **Item** é a linha do produto/serviço dentro do documento fiscal.
No sistema, chamamos de `fiscal_item`.

O item é a unidade principal de análise fiscal.

Campos essenciais:
- `item_number`
- `ncm`, `cfop`
- `cst_icms` e/ou `csosn` (dependendo do regime)
- `quantity`, `unit_price`, `total_value`
- para cada imposto relevante: base, alíquota e valor (ex.: ICMS/PIS/COFINS)

### 3.3 Importação
Uma **Importação** representa um lote enviado pelo usuário (normalmente um `.zip`).
No sistema:
- `importacao`: registro do lote (status, hash, contadores, `source_type`)
- `import_item`: cada XML individual extraído/listado do lote (status, erro)

A importação é processada de forma assíncrona:
- a API só registra e salva o arquivo (não parseia)
- workers processam extração e parsing via fila

### 3.4 Manifesto de Ingestão
Um **Manifesto** representa uma lista de XMLs já enviados para storage.

Campos típicos:
- `tenant_id`, `empresa_id`
- `object_key`
- `sha256`
- `size`
- metadados opcionais do arquivo origem

O manifesto é a fonte oficial para criação de `import_item` em cenários de alto volume.

Campos persistidos relevantes:
- `importacao.source_type = MANIFEST`
- `import_item.storage_object_key` para leitura direta do XML no storage

### 3.5 Agente de Ingestão
O **Agente** (desktop/headless) roda no ambiente do cliente para:
- coletar XMLs de diretórios locais
- fazer upload concorrente para storage S3-compatível
- gerar e enviar manifesto para a API
- retomar processamento em caso de falha/rede instável

---

## 4. Princípios do sistema

### 4.1 Fato fiscal vs. resultado fiscal
O banco armazena **fatos fiscais** (o que está declarado no XML) de forma normalizada.
Regras e cálculos de “recuperação” geram **resultados** que podem existir como relatórios/queries
ou como tabelas específicas no futuro — mas não devem poluir as tabelas de fato.

### 4.2 Idempotência
A chave `(empresa_id, access_key)` é única.
Reimportações devem:
- ignorar duplicados, ou
- registrar como duplicado (sem quebrar o lote).

### 4.3 Processamento assíncrono
Nunca processar parsing de XML dentro de request HTTP.
Sempre usar fila + workers para:
- resiliência
- retry
- controle de concorrência
- previsibilidade

Cliente externo não publica na fila diretamente; o backend é o único publicador oficial.

### 4.4 Parsing streaming
XML deve ser lido em streaming (StAX/SAX) para reduzir consumo de memória e permitir alto volume.

---

## 5. Regras e suposições

- Um `.zip` pode conter XMLs 55 e 65 misturados.
- Os XMLs podem estar em subpastas.
- Nem toda NFC-e terá destinatário (`dest_cnpj` pode ser nulo).
- Campos fiscais podem estar ausentes ou malformados; isso gera erro no `import_item`.

---

## 6. Glossário rápido

- **55**: NF-e
- **65**: NFC-e
- **CFOP**: Código Fiscal de Operações e Prestações
- **CST/CSOSN**: Códigos de situação tributária (ICMS)
- **NCM**: Nomenclatura Comum do Mercosul
- **Chave de acesso**: identificador de 44 caracteres da NF
