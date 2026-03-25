# FiscalAnalyzer – Agente de Ingestão

Este documento descreve a arquitetura, fluxo e contrato do **Agente de Ingestão**,
componente responsável por coletar XMLs fiscais no ambiente do cliente e enviá-los
ao backend para processamento.

---

## 1. Visão geral

O Agente é um aplicativo **headless** (console ou Windows Service) escrito em **C# / .NET**,
executado no ambiente do cliente (on-premises ou VPN).

```
[Diretório local com XMLs]
        ↓
   [Agente C#]
    ├── Calcula SHA-256
    ├── Upload concorrente → [Storage S3-compatível]
    └── POST /imports/manifest → [Backend FiscalAnalyzer]
                                        ↓
                              [Workers parse XML do storage]
```

O Agente **não**:
- Parseia regra fiscal.
- Publica direto no RabbitMQ.
- Persiste dados no banco de domínio.

Documento complementar obrigatório:
- `AGENT_INTEGRATION_CONTRACT.md` (contrato vigente + changelog de impactos no agente)

---

## 2. Linguagem e stack recomendada

| Decisão | Escolha | Motivo |
|---|---|---|
| Linguagem | C# / .NET 8+ | Nativo Windows, suporte a Windows Service, upload async robusto |
| Runtime | `publish --self-contained -r win-x64` | Zero dependência no cliente |
| Upload | `HttpClient` com `MultipartFormDataContent` | Suporte nativo a upload concorrente |
| Hash | `System.Security.Cryptography.SHA256` | Nativo, sem dependências |
| Watch | `FileSystemWatcher` | Monitoramento de diretório nativo |
| Retry | Polly (`Microsoft.Extensions.Http.Resilience`) | Retry e circuit breaker com fluent API |
| Config | `appsettings.json` + `IOptions<T>` | Padrão .NET, fácil de gerenciar |

---

## 3. Estrutura de projeto recomendada

```
FiscalAnalyzer.Agent/
├── Program.cs                   # Entry point, DI, host
├── appsettings.json             # Configuração de bucket, API, diretórios
├── Worker/
│   ├── AgentWorker.cs           # BackgroundService principal (loop de varredura)
│   └── ManifestWorker.cs        # Envia manifesto e aguarda confirmação
├── Upload/
│   ├── StorageUploader.cs       # Upload concorrente para S3
│   └── UploadResult.cs          # Resultado por arquivo (sucesso/falha)
├── Manifest/
│   ├── ManifestBuilder.cs       # Monta o JSON de manifesto
│   ├── ManifestEntry.cs         # Representa um XML no manifesto
│   └── ManifestSender.cs        # POST /imports/manifest
├── Hashing/
│   └── FileHasher.cs            # SHA-256 de arquivo em streaming
├── State/
│   └── UploadStateStore.cs      # Rastreia arquivos já enviados (SQLite local)
└── Config/
    └── AgentOptions.cs          # Tipagem forte das configurações
```

---

## 4. Configuração (`appsettings.json`)

```json
{
  "Agent": {
    "WatchDirectory": "C:\\NFe\\XMLs",
    "ScanIntervalSeconds": 60,
    "MaxUploadConcurrency": 4,
    "BackendBaseUrl": "https://fiscalanalyzer.interno/",
    "AgentId": "cliente-a-host-01",
    "ApiKey": "fa_live_xxxxxxxxxxxxxxxxxxxxxxxx"
  }
}
```

---

## 5. Fluxo detalhado

### 5.1 Varredura e upload

```
1. AgentWorker varre WatchDirectory (recursivo, *.xml)
2. Para cada arquivo:
   a. Calcula SHA-256 em streaming
   b. Verifica UploadStateStore — se já enviado com mesmo hash, ignora
   c. Chama POST /agent/upload-url (Bearer ApiKey)
   d. Recebe { uploadUrl, objectKey, expiresIn }
   e. Faz PUT no uploadUrl com content-type application/xml
      (chave determinística por hash — idempotente no backend)
   d. Registra resultado em UploadStateStore
3. Aguarda todos os uploads concluírem (SemaphoreSlim com MaxUploadConcurrency)
```

### 5.2 Geração e envio do manifesto

```
4. Agente chama POST /agent/session (Bearer ApiKey) para obter tenant/empresa do ciclo
5. ManifestBuilder monta lista de ManifestEntry para os arquivos com sucesso
6. ManifestSender faz POST /imports/manifest com Bearer ApiKey
7. Backend registra importacao + import_items em batch e enfileira parse
8. Agente registra importacaoId retornado no UploadStateStore

Observação:
- enviar `X-Agent-Id` estável nas chamadas autenticadas para melhorar identificação operacional e lockout.
```

### 5.3 Retry local (Polly)

- Upload: 3 tentativas, backoff exponencial 1s → 4s → 16s
- POST manifesto: 3 tentativas, backoff 2s → 8s
- Em `429 RATE_LIMITED`: respeitar `Retry-After` antes de nova tentativa
- Em caso de falha total: log estruturado + próxima varredura recomeça do ponto salvo

---

## 6. Contrato da API — POST /imports/manifest

Antes do manifesto, o agente usa também:

### `POST /agent/upload-url`

Request:

```json
{
  "sha256": "a3f5c2d1e8b4f7a2c9d3e6f1b8a5c2d4e7f0a1b3c6d9e2f5a8b1c4d7e0f3a6b9",
  "originalFileName": "NF-35240101234567.xml",
  "sizeBytes": 14320
}
```

Response:

```json
{
  "uploadUrl": "https://...assinada...",
  "objectKey": "1/10/a3f5c2d1e8b4f7a2c9d3e6f1b8a5c2d4e7f0a1b3c6d9e2f5a8b1c4d7e0f3a6b9.xml",
  "expiresIn": 900
}
```

### Request

```
POST /imports/manifest
Content-Type: application/json
Authorization: Bearer fa_live_xxxxxxxxx
```

```json
{
  "tenantId": 1,
  "empresaId": 10,
  "entries": [
    {
      "objectKey": "1/10/a3f5c2d1...xml",
      "sha256": "a3f5c2d1e8b4...",
      "sizeBytes": 14320,
      "originalFileName": "35240101234567000195550010000012341234567890-nfe.xml"
    }
  ]
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tenantId` | long | sim | Identificador do tenant |
| `empresaId` | long | sim | Identificador da empresa |
| `entries` | array | sim | Lista de XMLs enviados ao storage |
| `entries[].objectKey` | string | sim | Caminho no bucket |
| `entries[].sha256` | string | sim | Hash SHA-256 do arquivo |
| `entries[].sizeBytes` | long | não | Tamanho em bytes |
| `entries[].originalFileName` | string | não | Nome original para rastreio |

### Response 202 Accepted

```json
{
  "importacaoId": 42,
  "status": "RECEBIDO",
  "totalItems": 150
}
```

### Erros esperados

| HTTP | Situação |
|---|---|
| 401 | ApiKey ausente/inválida |
| 403 | ApiKey revogada/inativa ou tenant/empresa divergente |
| 429 | Rate limit/lockout ativo (usar `Retry-After`) |
| 400 | Manifesto vazio ou campos obrigatórios ausentes |
| 422 | `objectKey` não encontrado no storage |
| 500 | Erro interno — agente deve fazer retry |

---

## 7. Idempotência do agente

- A chave de upload `{tenantId}/{empresaId}/{sha256}.xml` é **determinística**.
  Reenviar o mesmo arquivo sobrescreve o mesmo objeto — seguro e sem custo extra.
- O backend garante idempotência pelo `fiscal_document_registry` (chave de acesso).
- O `UploadStateStore` local (SQLite) evita re-upload desnecessário entre varreduras,
  mas não é crítico para correção — apenas para eficiência de custo de storage.

---

## 8. Rastreabilidade

- Cada arquivo processado deve ser logado com: `sha256`, `objectKey`, `originalFileName`, resultado.
- Logs estruturados (JSON) recomendados com `Serilog` ou `Microsoft.Extensions.Logging`.
- O `importacaoId` retornado pelo backend deve ser persistido no `UploadStateStore`
  para rastrear quais lotes foram aceitos.

---

## 9. Distribuição

| Formato | Comando | Uso |
|---|---|---|
| Executável único | `dotnet publish -r win-x64 --self-contained -p:PublishSingleFile=true` | Distribuição simples, sem instalador |
| Windows Service | `sc create FiscalAgente binPath= "C:\FiscalAnalyzer.Agent.exe"` | Execução contínua em background |
| Task Scheduler | Via XML de tarefa agendada | Execução periódica sem serviço |

---

## 10. O que NÃO implementar no agente

- Não parsear XML fiscal — responsabilidade exclusiva do backend.
- Não publicar direto no RabbitMQ — sempre via `POST /imports/manifest`.
- Não manter estado de processamento fiscal — apenas rastrear uploads e importacaoIds.
- Não fazer `LIST` massivo no bucket para descobrir arquivos — varredura local é suficiente.
