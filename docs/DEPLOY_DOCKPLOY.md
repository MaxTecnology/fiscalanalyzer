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

| Variável | Obrigatória | Para que serve |
|---|---|---|
| `STORAGE_S3_ENDPOINT` | sim | Endpoint S3 compatível (Backblaze B2 S3 API). |
| `STORAGE_S3_ACCESS_KEY` | sim | Chave de acesso da aplicação no bucket. |
| `STORAGE_S3_SECRET_KEY` | sim | Segredo da aplicação no bucket. |
| `STORAGE_S3_BUCKET` | sim | Bucket de armazenamento dos XML/ZIP. |
| `STORAGE_S3_REGION` | não | Região lógica S3 (`us-east-1` por padrão). |
| `STORAGE_S3_PATH_STYLE_ACCESS` | não | Força estilo path-style (`false` por padrão). |
| `POSTGRES_DB` | sim | Nome do banco usado pela aplicação. |
| `POSTGRES_USER` | sim | Usuário de conexão do Postgres. |
| `POSTGRES_PASSWORD` | sim | Senha de conexão do Postgres. |
| `RABBITMQ_USER` | sim | Usuário do broker RabbitMQ. |
| `RABBITMQ_PASSWORD` | sim | Senha do broker RabbitMQ. |
| `REDIS_HOST` | recomendado | Host do Redis (rate-limit/lockout distribuído). |
| `REDIS_PORT` | recomendado | Porta do Redis (`6379` por padrão). |
| `REDIS_PASSWORD` | não | Senha do Redis, quando habilitada. |
| `APP_SECURITY_BOOTSTRAP_TOKEN` | recomendado | Token one-shot para `POST /auth/bootstrap-admin` em ambiente novo. |
| `APP_SECURITY_JWT_SECRET` | sim (fase front/login) | Segredo HMAC do JWT de usuário local (mínimo 32 chars). |
| `APP_SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS` | recomendado | Tempo de vida do access token JWT (segundos). |
| `APP_SECURITY_JWT_CLOCK_SKEW_SECONDS` | recomendado | Tolerância de clock na validação de expiração do JWT (segundos). |
| `APP_WEB_CORS_ALLOWED_ORIGINS` | sim (com front separado) | Lista de origens permitidas no CORS (separadas por vírgula). |
| `APP_TIMEZONE` | recomendado | Timezone padrão da API (ex.: `America/Fortaleza`). |
| `APP_SECURITY_AUTH_AUDIT_MODE` | recomendado | Nível de auditoria de auth (`off`, `failures`, `all`). |
| `APP_SECURITY_AUDIT_CLEANUP_ENABLED` | recomendado | Liga/desliga job de retenção de auditoria. |
| `APP_SECURITY_AUDIT_CLEANUP_CRON` | recomendado | Agenda do cleanup (`cron`, UTC). |
| `APP_SECURITY_AUDIT_CLEANUP_AUTH_RETENTION_DAYS` | recomendado | Dias de retenção para `agent_auth_audit`. |
| `APP_SECURITY_AUDIT_CLEANUP_UPLOAD_RETENTION_DAYS` | recomendado | Dias de retenção para `agent_upload_audit`. |
| `APP_SECURITY_AUDIT_CLEANUP_BATCH_SIZE` | recomendado | Quantidade por lote de limpeza. |
| `APP_SECURITY_AUDIT_CLEANUP_MAX_BATCHES` | recomendado | Limite de lotes por execução do cleanup. |
| `APP_SECURITY_RATE_LIMIT_ENABLED` | recomendado | Habilita rate-limit global das rotas do agent. |
| `APP_SECURITY_RATE_LIMIT_BACKEND` | recomendado | Backend de rate-limit (`redis` recomendado em produção). |
| `APP_SECURITY_RATE_LIMIT_REDIS_KEY_PREFIX` | recomendado | Prefixo das chaves de controle no Redis. |
| `APP_SECURITY_RATE_LIMIT_REDIS_TOKEN_BUCKET_TTL_SECONDS` | recomendado | TTL dos buckets de token em segundos. |
| `APP_SECURITY_RATE_LIMIT_AGENT_SESSION_RPS` | recomendado | Limite de requests/s para `/agent/session`. |
| `APP_SECURITY_RATE_LIMIT_AGENT_SESSION_BURST` | recomendado | Burst máximo para `/agent/session`. |
| `APP_SECURITY_RATE_LIMIT_AGENT_UPLOAD_URL_RPS` | recomendado | Limite de requests/s para `/agent/upload-url`. |
| `APP_SECURITY_RATE_LIMIT_AGENT_UPLOAD_URL_BURST` | recomendado | Burst máximo para `/agent/upload-url`. |
| `APP_SECURITY_RATE_LIMIT_AGENT_MANIFEST_RPS` | recomendado | Limite de requests/s para `/imports/manifest`. |
| `APP_SECURITY_RATE_LIMIT_AGENT_MANIFEST_BURST` | recomendado | Burst máximo para `/imports/manifest`. |
| `APP_SECURITY_RATE_LIMIT_ANONYMOUS_RPS` | recomendado | Limite para tráfego sem chave válida (fingerprint). |
| `APP_SECURITY_RATE_LIMIT_ANONYMOUS_BURST` | recomendado | Burst máximo para tráfego anônimo. |
| `APP_SECURITY_RATE_LIMIT_AUTH_FAILURE_THRESHOLD` | recomendado | Tentativas de auth inválidas antes de lockout. |
| `APP_SECURITY_RATE_LIMIT_AUTH_FAILURE_WINDOW_SECONDS` | recomendado | Janela de tempo para contar falhas de auth. |
| `APP_SECURITY_RATE_LIMIT_AUTH_FAILURE_LOCKOUT_SECONDS` | recomendado | Duração do lockout após exceder limiar. |

Observação:
- variáveis de Actuator (`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS`, `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`) são opcionais e usadas apenas para aumentar visibilidade operacional.

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

Upload de teste (requer JWT de usuário `ADMIN`/`OPERADOR`):

```bash
curl -s -H "Authorization: Bearer <JWT>" \
  -F file=@docs/nfe.zip \
  "https://<seu-dominio>/imports/upload?tenantId=99&empresaId=99"
```

Consulta:

```bash
curl -s -H "Authorization: Bearer <JWT>" "https://<seu-dominio>/imports/<IMPORT_ID>"
curl -s -H "Authorization: Bearer <JWT>" "https://<seu-dominio>/imports/<IMPORT_ID>/items?page=0&size=20"
```

---

## 5. Checklist de produção

- Não commitar segredos (`.env`, chaves B2).
- Trocar senhas default de Postgres/RabbitMQ.
- Habilitar backup do volume de Postgres.
- Monitorar DLQ (`import.extract.dlq`, `import.parse.dlq`).
- Restringir acesso ao RabbitMQ Management na rede interna/VPN.
- Monitorar métricas de segurança conforme `docs/SECURITY_OPERATIONS_RUNBOOK.md`.
