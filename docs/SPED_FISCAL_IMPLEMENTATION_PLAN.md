# FiscalAnalyzer — SPED Fiscal (EFD ICMS/IPI) Plano + Schema V1

Objetivo: definir a estratégia profissional para ingestão de SPED Fiscal,
conciliação com NF-e/NFC-e já importadas e exposição de análises para operação/front.

Data de referência: **2026-04-04**

---

## 1. Escopo do problema

Precisamos responder, por tenant/empresa/período:
- se todas as notas fiscais esperadas no SPED foram importadas no FiscalAnalyzer;
- se os valores de cabeçalho e impostos conciliam (SPED x XML);
- quais divergências exigem tratativa.

O foco inicial é **EFD ICMS/IPI** (arquivo TXT do SPED Fiscal), não EFD Contribuições.

---

## 2. Fonte oficial (referência normativa)

- Portal SPED (manuais): `https://sped.rfb.gov.br/item/show/1573`
- Guia Prático EFD ICMS/IPI (vigência atual): `https://sped.rfb.gov.br/item/show/8114`
- Nota Técnica EFD ICMS/IPI 2025.001 (leiaute 020): `https://sped.rfb.gov.br/item/show/7819`
- Ato COTEPE/ICMS 79/25: `https://www.confaz.fazenda.gov.br/legislacao/atos/2025/ato-cotepe-icms-79-25`

Regra de ouro:
- parser e validações devem ser versionados por **layout do SPED** (ex.: 020),
  sem hardcode “fixo para sempre”.

---

## 3. Registros mínimos do SPED para V1

Para a primeira versão de conciliação, implementar leitura/persistência de:

- `0000` (abertura do arquivo/período/layout)
- `0150` (participantes)
- `C100` (documento fiscal)
- `C170` (itens do documento)
- `C190` (analítico ICMS por CST/CFOP/ALIQ)
- `E110` (apuração ICMS consolidada)
- `E111` (ajustes da apuração)
- `9999` (encerramento/quantidade de linhas)

Registros fora desse escopo ficam para fases futuras.

---

## 4. Schema lógico V1 (proposto)

> Observação: implementação via Flyway incremental (não editar migrations já aplicadas).

### 4.1 Controle de arquivo SPED

Tabela `sped_fiscal_file`
- `id` (PK)
- `tenant_id`, `empresa_id` (FK composta para `empresa(id, tenant_id)`)
- `periodo_inicio`, `periodo_fim`
- `layout_version` (ex.: `020`)
- `hash_sha256`
- `storage_object_key` (arquivo original)
- `line_count_declared` (`9999.QTD_LIN`)
- `line_count_processed`
- `status` (`RECEBIDO`, `PROCESSANDO`, `PROCESSADO`, `FALHA`)
- `erro_codigo`, `erro_mensagem`
- `created_at`, `updated_at`

Constraints/índices:
- `UNIQUE (tenant_id, empresa_id, hash_sha256)` (idempotência de arquivo)
- índice `(tenant_id, empresa_id, periodo_inicio, periodo_fim)`

### 4.2 Documento SPED (C100)

Tabela `sped_fiscal_c100`
- `id` (PK)
- `file_id` (FK `sped_fiscal_file`)
- `tenant_id`, `empresa_id`
- `linha_origem`
- `ind_oper`, `ind_emit`, `cod_mod`, `cod_sit`
- `serie`, `num_doc`, `chv_nfe`
- `dt_doc`, `dt_es`
- `vl_doc`, `vl_desc`, `vl_merc`
- `vl_bc_icms`, `vl_icms`, `vl_bc_icms_st`, `vl_icms_st`
- `vl_ipi`, `vl_pis`, `vl_cofins`, `vl_pis_st`, `vl_cofins_st`
- `cod_part` (referência ao 0150)

Constraints/índices:
- `UNIQUE (file_id, linha_origem)`
- índice `(tenant_id, empresa_id, chv_nfe)`
- índice `(tenant_id, empresa_id, dt_doc)`

### 4.3 Item SPED (C170)

Tabela `sped_fiscal_c170`
- `id` (PK)
- `c100_id` (FK `sped_fiscal_c100`)
- `tenant_id`, `empresa_id`
- `linha_origem`
- `num_item`, `cod_item`, `descr_compl`
- `qtd`, `unid`, `vl_item`, `vl_desc`
- `cfop`, `cst_icms`
- `vl_bc_icms`, `aliq_icms`, `vl_icms`
- `vl_bc_ipi`, `aliq_ipi`, `vl_ipi`
- `cst_pis`, `vl_bc_pis`, `aliq_pis`, `vl_pis`
- `cst_cofins`, `vl_bc_cofins`, `aliq_cofins`, `vl_cofins`

Constraints/índices:
- `UNIQUE (c100_id, num_item)`
- índice `(tenant_id, empresa_id, cfop)`
- índice `(tenant_id, empresa_id, cod_item)`

### 4.4 Analítico SPED (C190)

Tabela `sped_fiscal_c190`
- `id` (PK)
- `c100_id` (FK `sped_fiscal_c100`)
- `tenant_id`, `empresa_id`
- `linha_origem`
- `cst_icms`, `cfop`, `aliq_icms`
- `vl_opr`, `vl_bc_icms`, `vl_icms`, `vl_bc_icms_st`, `vl_icms_st`, `vl_red_bc`, `vl_ipi`

Constraints/índices:
- `UNIQUE (c100_id, cst_icms, cfop, aliq_icms)`

### 4.5 Apuração SPED (E110/E111)

Tabela `sped_fiscal_e110`
- `id` (PK)
- `file_id` (FK)
- `tenant_id`, `empresa_id`
- campos principais de apuração ICMS do período

Tabela `sped_fiscal_e111`
- `id` (PK)
- `e110_id` (FK)
- `tenant_id`, `empresa_id`
- `cod_aj_apur`, `descr_compl_aj`, `vl_aj_apur`

### 4.6 Resultado de conciliação

Tabela `sped_fiscal_reconciliation`
- `id` (PK)
- `file_id` (FK)
- `tenant_id`, `empresa_id`
- `access_key` (quando aplicável)
- `tipo` (`FALTANTE_XML`, `FALTANTE_SPED`, `VALOR_DIVERGENTE`, `IMPOSTO_DIVERGENTE`, `OK`)
- `campo`
- `valor_sped`, `valor_xml`
- `severidade` (`ALTA`, `MEDIA`, `BAIXA`)
- `created_at`

Índices:
- `(tenant_id, empresa_id, tipo)`
- `(tenant_id, empresa_id, access_key)`

---

## 5. Regras de conciliação V1

### 5.1 Match principal

1. Chave de acesso (`chv_nfe`/`access_key`) como match canônico.
2. Fallback (somente quando sem chave): modelo + série + número + data + emitente.

### 5.2 Regras iniciais

- `C100.vl_doc` x `fiscal_document.total_amount`
- `C100.vl_icms` x `fiscal_document.total_icms`
- `C100.vl_pis` x `fiscal_document.total_pis`
- `C100.vl_cofins` x `fiscal_document.total_cofins`
- contagem de itens `C170` x `fiscal_item`
- agregados `C190` x agregados de itens no FiscalAnalyzer (por CFOP/CST/ALIQ)

### 5.3 Classificação de divergência

- `OK`
- `FALTANTE_XML` (existe no SPED, não existe em `fiscal_document`)
- `FALTANTE_SPED` (existe no XML importado do período, não consta no SPED)
- `VALOR_DIVERGENTE`
- `IMPOSTO_DIVERGENTE`

---

## 6. Fases de implementação

## Fase 8.1 — Fundação de ingestão SPED (**CONCLUÍDA em 2026-04-04**)

Entregas:
- endpoint `POST /sped/files/upload` para:
  - upload de arquivo SPED `.txt`;
  - upload de `.zip` com múltiplos `.txt`;
- persistência de `sped_fiscal_file` (migration `V24`);
- publicação AFTER_COMMIT para fila `import.sped.parse` via evento de domínio.

Aceite:
- arquivo recebido, hash calculado, idempotência garantida.
- para ZIP, cada entrada `.txt` elegível vira um registro deduplicado por hash no escopo tenant/empresa.

## Fase 8.2 — Parser streaming SPED (**EM ANDAMENTO**)

Entregas:
- parser linha a linha (sem carregar arquivo inteiro em memória);
- persistência de `0000/0150/C100/C170/C190/E110/E111/9999`.

Status atual:
- worker `ParseSpedConsumer` ativo na fila `import.sped.parse`;
- parser streaming já processa/persiste:
  - `0000` e `9999` (metadados + validação de linhas),
  - `0150`, `C100`, `C170`, `C190`, `E110`, `E111`;
- atualização de status em `sped_fiscal_file` (`PROCESSADO`/`FALHA`) e DLQ dedicada (`import.sped.parse.dlq`).

Aceite:
- processamento de arquivo grande com memória estável;
- status por arquivo (`PROCESSADO`/`FALHA`) com erro detalhado.

## Fase 8.3 — Motor de conciliação

Entregas:
- serviço assíncrono que cruza SPED com `fiscal_document`/`fiscal_item`;
- persistência de divergências em `sped_fiscal_reconciliation`.

Aceite:
- relatório com quantidades `OK`, `FALTANTE_*`, `DIVERGENTE`.

## Fase 8.4 — Read model + API de análise

Entregas:
- `GET /sped/files`
- `GET /sped/files/{id}`
- `GET /sped/files/{id}/reconciliation`
- `GET /sped/summary?tenantId&empresaId&periodoInicio&periodoFim`
- `POST /sped/files/{id}/reprocess`

Status atual:
- implementado:
  - `GET /sped/files`
  - `GET /sped/files/{id}`
  - `GET /sped/files/{id}/reconciliation` com comparação de:
    - total de documento (`C100.vl_doc` x `fiscal_document.total_amount`)
    - impostos (`ICMS/PIS/COFINS`)
    - quantidade de itens (`C170` x `fiscal_item`)
    - motivo de divergência (`divergenceReason`)
  - `GET /sped/reconciliation/summary` (período) com:
    - contadores por status de conciliação;
    - top CFOP/CST no SPED;
    - top CFOP/NCM no XML;
    - consolidado de apuração (`E110`) e ajustes (`E111`);
  - `GET /sped/reconciliation/divergences` para drill-down paginado por período
    com filtro opcional por status e chave de acesso;
  - `GET /sped/summary`
  - `POST /sped/files/{id}/reprocess`
- pendente:
  - expandir reconciliação agregada para visão consolidada de `E110/E111` por período

Aceite:
- front consegue listar arquivos, acompanhar processamento e visualizar divergências.

## Fase 8.5 — Reprocesso e governança

Entregas:
- endpoint de reprocesso por arquivo SPED com `dryRun`;
- trilha de auditoria.

Aceite:
- reprocesso idempotente sem duplicação.

## Fase 8.6 — Performance/operação

Entregas:
- métricas (tempo parse, linhas/s, divergências/s);
- tuning de batch de insert;
- testes com arquivo grande real.

Aceite:
- throughput validado em homolog com volume representativo.

---

## 7. Requisitos não funcionais obrigatórios

- Flyway como fonte da verdade (`ddl-auto=validate`).
- Parser streaming obrigatório.
- Idempotência por hash do arquivo e por chave de documento.
- Erros definitivos de layout/conteúdo: `FALHA` sem retry infinito.
- Observabilidade: logs estruturados + métricas por fase.

---

## 8. Impacto no Front (mínimo)

Tela “SPED Fiscal” precisa de:
- lista de arquivos SPED por período/status;
- detalhe com progresso de parse;
- painel de conciliação (cards + grid de divergências);
- filtros por tipo de divergência, modelo, data e chave de acesso.

---

## 9. Próximo passo imediato (execução)

Ordem recomendada:
1. Aprovar schema V1 e contratos de endpoint SPED.
2. Implementar Fase 8.1 + 8.2 com testes.
3. Subir endpoint de leitura mínima para front validar UX cedo.
