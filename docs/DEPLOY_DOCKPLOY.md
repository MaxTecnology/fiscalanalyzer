# Deploy em Nuvem com Dockploy

Este guia sobe o FiscalAnalyzer no Dockploy usando Backblaze B2 (S3 compatível) como storage.

---

## 1. Arquivo recomendado

Use:
- `docker/docker-compose.dockploy.yml`

Motivo:
- não expõe Postgres/RabbitMQ publicamente
- mantém apenas porta da aplicação (`8080`)
- já está preparado para variáveis de ambiente

---

## 2. Variáveis obrigatórias no Dockploy

Storage (Backblaze):
- `STORAGE_S3_ENDPOINT`
- `STORAGE_S3_ACCESS_KEY`
- `STORAGE_S3_SECRET_KEY`
- `STORAGE_S3_BUCKET`

Storage (opcionais):
- `STORAGE_S3_REGION` (default `us-east-1`)
- `STORAGE_S3_PATH_STYLE_ACCESS` (default `false`)

Banco:
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`

RabbitMQ:
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`

---

## 3. Passo a passo no Dockploy

1. Criar novo serviço/stack apontando para o repositório.
2. Selecionar `docker/docker-compose.dockploy.yml`.
3. Configurar todas as variáveis do item 2.
4. Fazer deploy.
5. Publicar a porta HTTP da aplicação (`8080`) no domínio desejado.

---

## 4. Pós-deploy (validação mínima)

Health:

```bash
curl -s https://<seu-dominio>/actuator/health
```

Upload de teste:

```bash
curl -s -F tenantId=99 -F empresaId=99 -F file=@docs/nfe.zip \
  https://<seu-dominio>/imports/upload
```

Consulta:

```bash
curl -s https://<seu-dominio>/imports/<IMPORT_ID>
curl -s "https://<seu-dominio>/imports/<IMPORT_ID>/items?page=0&size=20"
```

---

## 5. Checklist de produção

- Não commitar segredos (`.env`, chaves B2).
- Trocar senhas default de Postgres/RabbitMQ.
- Habilitar backup do volume de Postgres.
- Monitorar DLQ (`import.extract.dlq`, `import.parse.dlq`).
- Restringir acesso ao RabbitMQ Management na rede interna/VPN.
