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

## Testes (obrigatório)
- Toda feature deve incluir testes automatizados no mesmo PR.
- Mínimo por feature:
    - 1 teste de caminho feliz
    - 1 teste de falha/erro esperado
    - 1 teste de idempotência quando aplicável
- Para filas/workers: validar ACK/retry/DLQ conforme regra de negócio.
- Ao alterar parser: adicionar ou atualizar testes com XML realista.
- `mvn test` deve ficar verde antes de considerar a tarefa concluída.

## Custo e escala
- Em alto volume, preferir manifesto de arquivos em vez de descoberta por varredura massiva.
- Não enviar conteúdo de XML na fila; enviar apenas metadados (bucket/key/hash/id).
- Evitar chamadas desnecessárias de storage (`GET/HEAD/LIST`) para reduzir custo operacional.

## IA / Codex
- Não inventar entidades, colunas ou tabelas
- Não renomear colunas sem migration explícita
- Sempre explicar impacto da mudança
