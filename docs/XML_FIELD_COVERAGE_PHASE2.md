# FiscalAnalyzer — Cobertura de Campos XML (Fase 2)

Data de referência: **2026-03-26**

Escopo desta fase:

- ampliar persistência analítica para grupos de alto valor (ide/emit/dest/totais/protocolo/pagamentos/cobrança/observações)
- manter parser streaming para NF-e (55) e NFC-e (65)

Migration aplicada nesta fase:

- `V20__expand_fiscal_document_parse_coverage.sql`

## 1. Novas colunas em `fiscal_document`

Mapeamentos adicionados:

- ide:
  - `numero_nota` <- `ide/nNF`
  - `serie` <- `ide/serie`
  - `natureza_operacao` <- `ide/natOp`
  - `ambiente` <- `ide/tpAmb`
  - `finalidade_emissao` <- `ide/finNFe`
  - `consumidor_final` <- `ide/indFinal`
  - `presenca_comprador` <- `ide/indPres`
- emit:
  - `emit_nome` <- `emit/xNome`
  - `emit_ie` <- `emit/IE`
  - `emit_uf` <- `emit/enderEmit/UF`
- dest:
  - `dest_documento` <- `dest/CNPJ` ou `dest/CPF`
  - `dest_nome` <- `dest/xNome`
  - `dest_ie` <- `dest/IE`
  - `dest_uf` <- `dest/enderDest/UF`
- totais:
  - `total_frete` <- `total/ICMSTot/vFrete`
  - `total_desconto` <- `total/ICMSTot/vDesc`
  - `total_outros` <- `total/ICMSTot/vOutro`
  - `total_ipi` <- `total/ICMSTot/vIPI`
  - `total_tributos` <- `total/ICMSTot/vTotTrib`
- protocolo/autorização:
  - `protocolo_numero` <- `protNFe/infProt/nProt`
  - `protocolo_status` <- `protNFe/infProt/cStat`
  - `protocolo_motivo` <- `protNFe/infProt/xMotivo`
  - `protocolo_recebimento` <- `protNFe/infProt/dhRecbto`
- suplementares:
  - `qr_code_url` <- `infNFeSupl/qrCode`
- cobrança (fatura):
  - `fatura_numero` <- `cobr/fat/nFat`
  - `fatura_valor_original` <- `cobr/fat/vOrig`
  - `fatura_valor_desconto` <- `cobr/fat/vDesc`
  - `fatura_valor_liquido` <- `cobr/fat/vLiq`

## 2. Novas tabelas analíticas

## 2.1 `fiscal_document_payment`

Uma linha por `pag/detPag`:

- `payment_indicator` <- `indPag`
- `payment_type` <- `tPag`
- `payment_amount` <- `vPag`
- `card_integration_type` <- `card/tpIntegra`
- `card_cnpj` <- `card/CNPJ`
- `card_brand` <- `card/tBand`
- `card_authorization_code` <- `card/cAut`

## 2.2 `fiscal_document_duplicate`

Uma linha por `cobr/dup`:

- `duplicate_number` <- `nDup`
- `due_date` <- `dVenc`
- `amount` <- `vDup`

## 2.3 `fiscal_document_additional_info`

Persistência de informações adicionais:

- tipo `INF_CPL` <- `infAdic/infCpl`
- tipo `OBS_CONT` <- `infAdic/obsCont` (`field_name` recebe `@xCampo`, `text_value` recebe `xTexto`)

## 3. Cobertura validada por testes

- parser unitário:
  - `NfeXmlParserTest` com XMLs reais de `docs/xmls/nfe.xml` e `docs/xmls/nfce.xml`
- service unitário:
  - `ParseXmlServiceTest` valida persistência dos novos grupos:
    - pagamentos
    - duplicatas
    - observações adicionais

## 4. Observações de modelagem

- `dest_cnpj` foi mantido por compatibilidade de leitura.
- `dest_documento` é a coluna semântica para documento do destinatário (CNPJ/CPF).
- próximos passos (Fase 3) devem incluir estratégia de cobertura complementar total para campos não estruturados.
