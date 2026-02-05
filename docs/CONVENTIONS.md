# FiscalAnalyzer – Project Conventions

## Código
- Java 21
- Spring Boot 4.x
- Pacotes por domínio (não por camada técnica)

## Banco
- Nunca alterar migrations já executadas
- Sempre criar migrations incrementais (Vx__)
- Não usar ddl-auto=create ou update

## ORM
- Entities refletem o schema do Flyway
- Hibernate roda apenas em modo validate

## Debug
- Erros devem ser resolvidos um por vez
- Sempre identificar a causa raiz antes de propor solução

## IA / Codex
- Não inventar entidades, colunas ou tabelas
- Não renomear colunas sem migration explícita
- Sempre explicar impacto da mudança
