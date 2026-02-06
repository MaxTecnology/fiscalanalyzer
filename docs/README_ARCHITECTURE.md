# FiscalAnalyzer — Architecture Reference

Este documento descreve a arquitetura técnica do FiscalAnalyzer.
Ele explica os fluxos, responsabilidades e decisões arquiteturais,
servindo como guia para desenvolvimento humano e ferramentas de IA.

---

## 1. Visão geral

O FiscalAnalyzer é uma aplicação backend desenvolvida em **Java 21 + Spring Boot**,
projetada para ingestão e análise de grandes volumes de documentos fiscais
(NF-e modelo 55 e NFC-e modelo 65).

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
- um único parser para NF-e e NFC-e
- diferenciação por `ide.mod` (55 ou 65)

Responsabilidades:
- ler XML
- validar estrutura mínima
- mapear para modelos canônicos internos
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

---

### 2.6 Armazenamento de Arquivos
Arquivos originais são mantidos para:
- auditoria
- reprocessamento
- rastreabilidade

Tecnologia:
- MinIO (compatível com S3)

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
3. Modelo (55/65) é identificado
4. Documento e itens são mapeados
5. Dados são persistidos
6. Status do item é atualizado

---

### 3.4 Finalização
Quando todos os itens de uma importação são processados:
- a importação é marcada como concluída
- erros ficam disponíveis para análise
- relatórios podem ser gerados

---

## 4. Estrutura de pacotes (alto nível)

A estrutura do código segue o domínio do problema:

br.com.techbr.fiscalanalyzer
├── config # Configurações Spring, fila, storage, datasource
├── common # Enums, value objects, exceções comuns
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

---

## 6. Escalabilidade e futuro

A arquitetura foi desenhada para permitir:
- aumento de workers horizontalmente
- inclusão futura de SPED
- inclusão de novos modelos fiscais
- separação em microserviços, se necessário

Nenhuma decisão atual bloqueia essas evoluções.

---

## 7. O que NÃO fazer

- Não processar XML em endpoints HTTP
- Não carregar XML inteiro em memória
- Não misturar regra fiscal com persistência
- Não persistir resultado fiscal na tabela de fatos

---

## 8. Como usar este documento com IA (Codex)

Antes de implementar qualquer código:
1. Ler este arquivo (`README_ARCHITECTURE.md`)
2. Ler o `DOMAIN.md`
3. Implementar apenas o escopo solicitado
4. Respeitar responsabilidades dos pacotes

Isso garante consistência e evita código fora do padrão arquitetural.
