# FiscalAnalyzer — Cobertura de Campos XML (Fase 1)

Data de referência: **2026-03-26**

Escopo deste documento: validar cobertura de **todos os campos já existentes no schema atual**
(`fiscal_document`, `fiscal_item`, `import_item`) com parser streaming.

Arquivos de referência:

- `docs/xmls/nfe.xml`
- `docs/xmls/nfce.xml`

## 1. fiscal_document

| Coluna | Origem XML | Status Fase 1 |
|---|---|---|
| `model` | `ide/mod` | OK |
| `access_key` | `infNFe/@Id` (fallback `protNFe/infProt/chNFe`) | OK |
| `issue_date` | `ide/dhEmi` ou `ide/dEmi` | OK |
| `issue_datetime` | `ide/dhEmi` | OK |
| `operation_type` | `ide/tpNF` (`0=E`, `1=S`) | OK |
| `emit_cnpj` | `emit/CNPJ` (fallback `emit/CPF`) | OK |
| `dest_cnpj` | `dest/CNPJ` (fallback `dest/CPF`) | OK no modelo atual |
| `total_products` | `total/ICMSTot/vProd` | OK |
| `total_amount` | `total/ICMSTot/vNF` | OK |
| `total_icms` | `total/ICMSTot/vICMS` | OK |
| `total_pis` | `total/ICMSTot/vPIS` | OK |
| `total_cofins` | `total/ICMSTot/vCOFINS` | OK |
| `xml_path` | pipeline (zip entry ou object key) | OK |
| `xml_hash` | SHA-256 do XML lido no parse | OK |

Observação:

- `dest_cnpj` armazena o documento do destinatário no modelo atual (CNPJ ou CPF).
  Na Fase 2 será normalizado para campo semântico dedicado.

## 2. fiscal_item

| Coluna | Origem XML | Status Fase 1 |
|---|---|---|
| `item_number` | `det/@nItem` | OK |
| `product_code` | `det/prod/cProd` | OK |
| `product_description` | `det/prod/xProd` | OK |
| `ncm` | `det/prod/NCM` | OK |
| `cfop` | `det/prod/CFOP` | OK |
| `cst_icms` | `det/imposto/ICMS/.../CST` | OK |
| `csosn` | `det/imposto/ICMS/.../CSOSN` | OK |
| `quantity` | `det/prod/qCom` | OK |
| `unit_price` | `det/prod/vUnCom` | OK |
| `total_value` | `det/prod/vProd` | OK |
| `icms_base` | `det/imposto/ICMS/.../vBC` | OK quando aplicável |
| `icms_rate` | `det/imposto/ICMS/.../pICMS` | OK quando aplicável |
| `icms_value` | `det/imposto/ICMS/.../vICMS` | OK quando aplicável |
| `pis_base` | `det/imposto/PIS/.../vBC` | OK quando aplicável |
| `pis_rate` | `det/imposto/PIS/.../pPIS` | OK quando aplicável |
| `pis_value` | `det/imposto/PIS/.../vPIS` | OK quando aplicável |
| `cofins_base` | `det/imposto/COFINS/.../vBC` | OK quando aplicável |
| `cofins_rate` | `det/imposto/COFINS/.../pCOFINS` | OK quando aplicável |
| `cofins_value` | `det/imposto/COFINS/.../vCOFINS` | OK quando aplicável |

## 3. import_item (campos de parse)

| Coluna | Origem | Status Fase 1 |
|---|---|---|
| `model` | parser (`ide/mod`) | OK |
| `access_key` | parser (`infNFe/@Id` ou `chNFe`) | OK |
| `issue_date` | parser (`dhEmi/dEmi`) | OK |
| `xml_hash` | SHA-256 no parse | OK |
| `xml_size` | bytes efetivamente lidos no parse (fallback quando ausente) | OK |
| `status` | fluxo do worker de parse | OK |
| `erro_codigo` / `erro_mensagem` | validação/erro de parse | OK |

## 4. Fora do escopo da Fase 1 (entra na Fase 2)

Campos de alto valor que ainda não possuem estrutura dedicada no schema:

- dados completos de `ide` (ex.: `natOp`, `serie`, `nNF`, `indPres`, `finNFe`)
- dados completos de `emit` e `dest` (nome, IE, endereço, email)
- protocolo/autorização (`nProt`, `cStat`, `xMotivo`, `dhRecbto`)
- grupos de pagamento (`pag/detPag/card`)
- grupos de cobrança (`cobr/fat/dup`)
- observações adicionais (`infAdic/obsCont`, `infCpl`)
- campos complementares de tributos (ex.: IPI detalhado, `vTotTrib`, ST/FCP detalhados)
