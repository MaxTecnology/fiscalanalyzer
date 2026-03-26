# FiscalAnalyzer — Plano Profissional de Cobertura Total de XML (NF-e/NFC-e)

Data de referência: **2026-03-26**

## 1. Objetivo

Garantir cobertura de dados fiscal em nível profissional para NF-e (55) e NFC-e (65), com:

- extração consistente por parser streaming
- persistência auditável e orientada a análise
- evolução segura via Flyway (sem editar migrations aplicadas)
- testes automatizados por cenário fiscal relevante

Este plano considera os XMLs de referência:

- `docs/xmls/nfe.xml`
- `docs/xmls/nfce.xml`

## 2. Decisão arquitetural

Para evitar fragilidade e custo excessivo com schema extremamente largo, o modelo será **híbrido**:

- **camada analítica estruturada** (colunas/tabelas para consultas frequentes)
- **camada complementar completa** (persistência canônica dos campos adicionais para cobertura total)

Regras:

- migrations novas (`V20+`) para toda evolução
- `ddl-auto=validate` mantido
- parser continua streaming (sem carregar XML inteiro em memória)
- idempotência por `fiscal_document_registry` preservada

## 3. Estado atual (resumo)

O parser atual já cobre o núcleo:

- documento: modelo, chave, datas, operação, emit/dest (CNPJ), totais principais
- item: identificação do produto + ICMS/PIS/COFINS básicos

Gaps observados nos XMLs de referência:

- destinatário CPF (NFC-e) não mapeado para campo dedicado
- grupos de pagamento (`pag/detPag/card`) não persistidos
- grupos de cobrança (`cobr/fat/dup`) não persistidos
- protocolo/autorização (`protNFe/infProt`) não persistido em estrutura própria
- diversos campos de ide/emit/dest/transporte e tributos complementares não persistidos

## 4. Fases de implementação

## Fase 1 — Consolidação de cobertura do modelo atual

Escopo:

- garantir preenchimento consistente de todas as colunas já existentes
- corrigir lacunas de mapeamento relevantes sem quebra de compatibilidade
- ampliar testes unitários/integrados com os XMLs de `docs/xmls`

Entregáveis:

- parser validado para NF-e e NFC-e reais
- matriz `campo XML -> coluna atual -> status`
- testes cobrindo campos obrigatórios e cenários de nulos fiscais válidos

## Fase 2 — Expansão estruturada do schema (V20+)

Escopo:

- ampliar `fiscal_document` e `fiscal_item` com campos de alto valor analítico
- criar tabelas filhas para grupos repetíveis (pagamentos, duplicatas, observações, protocolo)
- manter índices focados em leitura operacional e relatórios

Entregáveis:

- migrations incrementais (`V20..V2x`)
- entities/repositories/serviços atualizados
- parser streaming expandido + testes

## Fase 3 — Cobertura complementar total

Escopo:

- garantir retenção completa dos campos não estruturados na camada analítica
- manter capacidade de auditoria e reprocessamento

Entregáveis:

- persistência complementar implementada
- contrato de dados documentado para backend/agent/front
- validação de custo/performance em lote alto

## Fase 4 — Reprocessamento controlado e validação de produção

Escopo:

- reprocessar amostra representativa
- validar consistência entre storage, import_item e documentos finais
- fechar checklist de readiness

Entregáveis:

- roteiro de backfill/reprocesso
- relatório de cobertura de campos
- checklist final de homologação

## 5. Critérios de aceite globais

- nenhum checksum mismatch de Flyway
- aplicação sobe com `ddl-auto=validate`
- parser sem regressão para fluxo ZIP e MANIFEST
- cobertura comprovada por testes para NF-e e NFC-e
- documentação atualizada em `docs/`

## 6. Sequência operacional recomendada

1. Executar Fase 1 e fechar matriz de cobertura atual.
2. Priorizar campos de maior impacto fiscal/operacional na Fase 2.
3. Implementar camada complementar total na Fase 3.
4. Reprocessar e validar em homologação na Fase 4.
