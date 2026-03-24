# FiscalAnalyzer.Agent – Prompt de Construção

Você está atuando como engenheiro sênior no projeto **FiscalAnalyzer.Agent**.

---

## 1. Contexto fixo

Este é um projeto **separado** do backend FiscalAnalyzer (Java/Spring Boot).
O agente é um aplicativo C# / .NET 9 que roda no ambiente do cliente (Windows ou Linux),
coleta XMLs fiscais de diretórios locais e os envia ao backend para processamento.

**O agente nunca:**
- Parseia regra fiscal.
- Acessa o banco de dados do backend.
- Publica diretamente no RabbitMQ.
- Faz `LIST` massivo no bucket para descobrir arquivos.

**O agente sempre:**
- Calcula SHA-256 antes de qualquer upload.
- Usa chave de objeto determinística no storage (`{tenantId}/{empresaId}/{sha256}.xml`).
- Envia manifesto ao backend via `POST /imports/manifest`.
- Registra estado local em SQLite para retomada após falha.

---

## 2. Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | C# 13 / .NET 9 |
| Host | `IHostedService` via `Microsoft.Extensions.Hosting` |
| HTTP Client | `HttpClient` com `IHttpClientFactory` |
| Retry HTTP | `Microsoft.Extensions.Http.Resilience` (Polly v8) |
| Storage S3 | `AWSSDK.S3` (ou `Minio` SDK — configurável) |
| Estado local | SQLite via `Microsoft.Data.Sqlite` + Dapper |
| Logs | `Microsoft.Extensions.Logging` + `Serilog.Extensions.Hosting` + sink Console JSON |
| Config | `appsettings.json` + variáveis de ambiente + `IOptions<AgentOptions>` |
| Testes | `xUnit` + `NSubstitute` + `Microsoft.Extensions.Logging.Testing` |

---

## 3. Estrutura completa do projeto

```
FiscalAnalyzer.Agent/
├── FiscalAnalyzer.Agent.csproj
├── Program.cs
├── appsettings.json
├── appsettings.Development.json
│
├── Config/
│   ├── AgentOptions.cs           # Configuração raiz tipada
│   └── StorageOptions.cs         # Sub-config do storage S3
│
├── Worker/
│   └── AgentWorker.cs            # BackgroundService: loop de varredura + upload + manifesto
│
├── Scanning/
│   └── XmlFileScanner.cs         # Varre diretório recursivamente, filtra *.xml
│
├── Hashing/
│   └── FileHasher.cs             # SHA-256 em streaming (não carrega arquivo em memória)
│
├── State/
│   ├── IUploadStateStore.cs      # Interface do repositório de estado local
│   ├── SqliteUploadStateStore.cs # Implementação com SQLite + Dapper
│   └── UploadRecord.cs           # Modelo do registro de estado
│
├── Upload/
│   ├── IStorageUploader.cs       # Interface de upload
│   ├── S3StorageUploader.cs      # Implementação com AWS SDK / MinIO
│   └── UploadResult.cs           # record: FilePath, ObjectKey, Sha256, Success, ErrorMessage
│
├── Manifest/
│   ├── ManifestEntry.cs          # record: ObjectKey, Sha256, SizeBytes, OriginalFileName
│   ├── ManifestRequest.cs        # DTO para POST /imports/manifest
│   ├── ManifestResponse.cs       # DTO da resposta: ImportacaoId, Status, TotalItems
│   └── ManifestSender.cs         # POST /imports/manifest com retry Polly
│
└── Tests/ (projeto separado: FiscalAnalyzer.Agent.Tests)
    ├── FiscalAnalyzer.Agent.Tests.csproj
    ├── FileHasherTests.cs
    ├── XmlFileScannerTests.cs
    ├── AgentWorkerTests.cs
    └── ManifestSenderTests.cs
```

---

## 4. Arquivo de projeto — `FiscalAnalyzer.Agent.csproj`

```xml
<Project Sdk="Microsoft.NET.Sdk.Worker">
  <PropertyGroup>
    <TargetFramework>net9.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <RootNamespace>FiscalAnalyzer.Agent</RootNamespace>
    <AssemblyName>FiscalAnalyzer.Agent</AssemblyName>
  </PropertyGroup>

  <ItemGroup>
    <PackageReference Include="Microsoft.Extensions.Hosting" Version="9.0.*" />
    <PackageReference Include="Microsoft.Extensions.Http.Resilience" Version="9.0.*" />
    <PackageReference Include="AWSSDK.S3" Version="3.7.*" />
    <PackageReference Include="Microsoft.Data.Sqlite" Version="9.0.*" />
    <PackageReference Include="Dapper" Version="2.1.*" />
    <PackageReference Include="Serilog.Extensions.Hosting" Version="8.*" />
    <PackageReference Include="Serilog.Sinks.Console" Version="6.*" />
    <PackageReference Include="Serilog.Formatting.Compact" Version="3.*" />
  </ItemGroup>
</Project>
```

---

## 5. Configuração

### `appsettings.json`

```json
{
  "Serilog": {
    "MinimumLevel": "Information"
  },
  "Agent": {
    "TenantId": 0,
    "EmpresaId": 0,
    "WatchDirectory": "C:\\NFe\\XMLs",
    "ScanIntervalSeconds": 60,
    "MaxUploadConcurrency": 4,
    "BackendBaseUrl": "https://fiscalanalyzer.interno/",
    "StateDatabasePath": "agent-state.db",
    "Storage": {
      "Endpoint": "https://s3.us-east-005.backblazeb2.com",
      "Bucket": "fiscalanalyzer-xmls",
      "Region": "us-east-005",
      "AccessKey": "",
      "SecretKey": ""
    }
  }
}
```

### `appsettings.Development.json`

```json
{
  "Serilog": { "MinimumLevel": "Debug" },
  "Agent": {
    "TenantId": 99,
    "EmpresaId": 99,
    "WatchDirectory": "./sample-xmls",
    "ScanIntervalSeconds": 10,
    "BackendBaseUrl": "http://localhost:8080/",
    "StateDatabasePath": "agent-state-dev.db",
    "Storage": {
      "Endpoint": "http://localhost:9000",
      "Bucket": "fiscalanalyzer-dev",
      "Region": "us-east-1",
      "AccessKey": "minioadmin",
      "SecretKey": "minioadmin"
    }
  }
}
```

---

## 6. Modelos de configuração

### `Config/AgentOptions.cs`

```csharp
namespace FiscalAnalyzer.Agent.Config;

public class AgentOptions
{
    public const string Section = "Agent";

    public long TenantId { get; set; }
    public long EmpresaId { get; set; }
    public string WatchDirectory { get; set; } = string.Empty;
    public int ScanIntervalSeconds { get; set; } = 60;
    public int MaxUploadConcurrency { get; set; } = 4;
    public string BackendBaseUrl { get; set; } = string.Empty;
    public string StateDatabasePath { get; set; } = "agent-state.db";
    public StorageOptions Storage { get; set; } = new();
}
```

### `Config/StorageOptions.cs`

```csharp
namespace FiscalAnalyzer.Agent.Config;

public class StorageOptions
{
    public string Endpoint { get; set; } = string.Empty;
    public string Bucket { get; set; } = string.Empty;
    public string Region { get; set; } = "us-east-1";
    public string AccessKey { get; set; } = string.Empty;
    public string SecretKey { get; set; } = string.Empty;
}
```

---

## 7. Entry point — `Program.cs`

```csharp
using FiscalAnalyzer.Agent.Config;
using FiscalAnalyzer.Agent.Manifest;
using FiscalAnalyzer.Agent.Scanning;
using FiscalAnalyzer.Agent.Hashing;
using FiscalAnalyzer.Agent.State;
using FiscalAnalyzer.Agent.Upload;
using FiscalAnalyzer.Agent.Worker;
using Serilog;
using Serilog.Formatting.Compact;

Log.Logger = new LoggerConfiguration()
    .WriteTo.Console(new CompactJsonFormatter())
    .CreateBootstrapLogger();

var host = Host.CreateDefaultBuilder(args)
    .UseSerilog((ctx, services, config) => config
        .ReadFrom.Configuration(ctx.Configuration)
        .WriteTo.Console(new CompactJsonFormatter()))
    .ConfigureServices((ctx, services) =>
    {
        services.Configure<AgentOptions>(ctx.Configuration.GetSection(AgentOptions.Section));

        // Estado local
        services.AddSingleton<IUploadStateStore, SqliteUploadStateStore>();

        // Infraestrutura
        services.AddSingleton<FileHasher>();
        services.AddSingleton<XmlFileScanner>();
        services.AddSingleton<IStorageUploader, S3StorageUploader>();

        // HTTP com retry Polly
        services.AddHttpClient<ManifestSender>(client =>
        {
            var opts = ctx.Configuration
                .GetSection(AgentOptions.Section)
                .Get<AgentOptions>()!;
            client.BaseAddress = new Uri(opts.BackendBaseUrl);
            client.Timeout = TimeSpan.FromSeconds(30);
        }).AddStandardResilienceHandler();

        // Worker principal
        services.AddHostedService<AgentWorker>();
    })
    .Build();

await host.RunAsync();
```

---

## 8. Especificação de cada classe

### `Hashing/FileHasher.cs`

**Responsabilidade:** Calcular SHA-256 de um arquivo em streaming sem carregá-lo em memória.

```csharp
// Método público:
Task<string> ComputeAsync(string filePath, CancellationToken ct = default);
// Retorna: hex lowercase de 64 chars
// Lança: IOException se arquivo não puder ser lido
```

---

### `Scanning/XmlFileScanner.cs`

**Responsabilidade:** Listar todos os arquivos `.xml` recursivamente em um diretório.

```csharp
// Método público:
IEnumerable<FileInfo> Scan(string directory);
// Retorna: FileInfo de cada .xml encontrado (case-insensitive na extensão)
// Lança: DirectoryNotFoundException se diretório não existir
// Não lança para arquivos inacessíveis — apenas loga warning e continua
```

---

### `State/UploadRecord.cs`

```csharp
namespace FiscalAnalyzer.Agent.State;

public class UploadRecord
{
    public long Id { get; set; }
    public string FilePath { get; set; } = string.Empty;
    public string Sha256 { get; set; } = string.Empty;
    public string ObjectKey { get; set; } = string.Empty;
    public long SizeBytes { get; set; }
    public bool Uploaded { get; set; }
    public long? ImportacaoId { get; set; }           // preenchido após POST manifesto
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
```

### `State/IUploadStateStore.cs`

```csharp
namespace FiscalAnalyzer.Agent.State;

public interface IUploadStateStore
{
    Task InitializeAsync(CancellationToken ct = default);

    /// Retorna o registro existente se sha256 já foi uploaded com sucesso.
    Task<UploadRecord?> FindUploadedAsync(string sha256, CancellationToken ct = default);

    /// Persiste ou atualiza um registro de upload.
    Task SaveAsync(UploadRecord record, CancellationToken ct = default);

    /// Retorna todos os registros uploaded mas ainda não vinculados a importacaoId.
    Task<IReadOnlyList<UploadRecord>> GetPendingManifestAsync(CancellationToken ct = default);

    /// Marca um conjunto de registros com o importacaoId retornado pelo backend.
    Task MarkManifestSentAsync(IEnumerable<string> sha256List, long importacaoId, CancellationToken ct = default);
}
```

### `State/SqliteUploadStateStore.cs`

**Responsabilidade:** Implementar `IUploadStateStore` usando SQLite + Dapper.

Schema da tabela (criado no `InitializeAsync`):

```sql
CREATE TABLE IF NOT EXISTS upload_record (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path    TEXT NOT NULL,
    sha256       TEXT NOT NULL UNIQUE,
    object_key   TEXT NOT NULL,
    size_bytes   INTEGER NOT NULL DEFAULT 0,
    uploaded     INTEGER NOT NULL DEFAULT 0,  -- 0=false, 1=true
    importacao_id INTEGER,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_upload_sha256 ON upload_record(sha256);
CREATE INDEX IF NOT EXISTS idx_upload_pending ON upload_record(uploaded, importacao_id);
```

**Regras:**
- `InitializeAsync` deve criar o arquivo SQLite e a tabela se não existirem.
- `SaveAsync` deve fazer `INSERT OR REPLACE`.
- O caminho do arquivo SQLite vem de `AgentOptions.StateDatabasePath`.

---

### `Upload/UploadResult.cs`

```csharp
namespace FiscalAnalyzer.Agent.Upload;

public record UploadResult(
    string FilePath,
    string ObjectKey,
    string Sha256,
    long SizeBytes,
    bool Success,
    string? ErrorMessage
);
```

### `Upload/IStorageUploader.cs`

```csharp
namespace FiscalAnalyzer.Agent.Upload;

public interface IStorageUploader
{
    /// Faz upload de um arquivo para o storage S3-compatível.
    /// ObjectKey é calculado internamente: {tenantId}/{empresaId}/{sha256}.xml
    Task<UploadResult> UploadAsync(
        string filePath,
        string sha256,
        long tenantId,
        long empresaId,
        CancellationToken ct = default);
}
```

### `Upload/S3StorageUploader.cs`

**Responsabilidade:** Implementar `IStorageUploader` usando `AWSSDK.S3`.

**Regras:**
- Usar `AmazonS3Config` com `ServiceURL` apontando para `StorageOptions.Endpoint`.
- `ForcePathStyle = true` (necessário para MinIO e Backblaze B2).
- Content-Type do objeto: `application/xml`.
- Em caso de falha de rede: deixar a exceção propagar (o caller faz retry via Polly ou captura).
- Nunca carregar o arquivo inteiro em memória: usar `new FileStream` com `TransferUtility`
  ou `PutObjectRequest` com `FilePath`.

```csharp
// Assinatura do construtor:
public S3StorageUploader(IOptions<AgentOptions> options, ILogger<S3StorageUploader> logger)
```

---

### `Manifest/ManifestEntry.cs`

```csharp
namespace FiscalAnalyzer.Agent.Manifest;

public record ManifestEntry(
    string ObjectKey,
    string Sha256,
    long? SizeBytes,
    string? OriginalFileName
);
```

### `Manifest/ManifestRequest.cs`

```csharp
namespace FiscalAnalyzer.Agent.Manifest;

public record ManifestRequest(
    long TenantId,
    long EmpresaId,
    IReadOnlyList<ManifestEntry> Entries
);
```

### `Manifest/ManifestResponse.cs`

```csharp
namespace FiscalAnalyzer.Agent.Manifest;

public record ManifestResponse(
    long ImportacaoId,
    string Status,
    int TotalItems
);
```

### `Manifest/ManifestSender.cs`

**Responsabilidade:** Enviar `POST /imports/manifest` ao backend.

```csharp
// Assinatura do construtor:
public ManifestSender(HttpClient httpClient, ILogger<ManifestSender> logger)

// Método público:
Task<ManifestResponse> SendAsync(ManifestRequest request, CancellationToken ct = default);
// Lança: HttpRequestException em caso de falha HTTP não recuperável
// Lança: InvalidOperationException se response body não for deserializável
```

**Regras:**
- `POST /imports/manifest` com `Content-Type: application/json`.
- Serializar com `System.Text.Json` (camelCase).
- Em resposta 4xx (exceto 429): não fazer retry — lançar exceção com body da resposta.
- Em resposta 5xx ou timeout: deixar o Polly (configurado no `HttpClient`) fazer retry.

---

### `Worker/AgentWorker.cs`

**Responsabilidade:** Loop principal do agente. Herda de `BackgroundService`.

**Fluxo por iteração:**

```
1. Chamar XmlFileScanner.Scan(WatchDirectory)
2. Para cada arquivo:
   a. Chamar FileHasher.ComputeAsync(filePath)
   b. Chamar IUploadStateStore.FindUploadedAsync(sha256)
      → se já uploaded e importacaoId preenchido: pular
      → se já uploaded mas sem importacaoId: adicionar ao lote do manifesto
      → se não uploaded: fazer upload
3. Upload concorrente (SemaphoreSlim com MaxUploadConcurrency):
   - Chamar IStorageUploader.UploadAsync(...)
   - Em sucesso: IUploadStateStore.SaveAsync(record com Uploaded=true)
   - Em falha: logar warning, continuar com próximo
4. Coletar registros pendentes de manifesto: IUploadStateStore.GetPendingManifestAsync()
5. Se lista não vazia: ManifestSender.SendAsync(ManifestRequest)
6. Em sucesso: IUploadStateStore.MarkManifestSentAsync(sha256List, importacaoId)
7. Aguardar ScanIntervalSeconds antes da próxima iteração
8. CancellationToken cancelado: sair do loop com log graceful
```

**Regras:**
- Exceção em arquivo individual não deve derrubar a iteração inteira.
- Exceção no envio do manifesto: logar erro, não marcar como enviado, tentar na próxima iteração.
- Log obrigatório no início e fim de cada iteração com contadores: encontrados, já-enviados, novos-uploads, falhas, manifesto-enviado.

---

## 9. Serialização JSON

Usar `System.Text.Json` com:
```csharp
new JsonSerializerOptions
{
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
}
```

Registrar como singleton no DI se usado em múltiplos lugares.

---

## 10. Contrato da API do backend

### `POST /imports/manifest`

**Request body:**
```json
{
  "tenantId": 99,
  "empresaId": 99,
  "entries": [
    {
      "objectKey": "99/99/a3f5c2d1e8b4f7a2c9d3e6f1b8a5c2d4e7f0a1b3c6d9e2f5a8b1c4d7e0f3a6b9.xml",
      "sha256": "a3f5c2d1e8b4f7a2c9d3e6f1b8a5c2d4e7f0a1b3c6d9e2f5a8b1c4d7e0f3a6b9",
      "sizeBytes": 14320,
      "originalFileName": "NF-35240101234567.xml"
    }
  ]
}
```

**Response 202:**
```json
{
  "importacaoId": 42,
  "status": "RECEBIDO",
  "totalItems": 1
}
```

**Erros:**

| HTTP | O agente deve... |
|---|---|
| 400 | Logar erro, não fazer retry (dado inválido) |
| 422 | Logar erro, não fazer retry (objeto não encontrado no storage) |
| 429 | Aguardar e fazer retry (rate limit) |
| 5xx | Fazer retry via Polly (instabilidade do backend) |
| Timeout | Fazer retry via Polly |

---

## 11. Política de retry (Polly via `AddStandardResilienceHandler`)

O handler padrão do `Microsoft.Extensions.Http.Resilience` já configura:
- Retry: 3 tentativas, backoff exponencial com jitter
- Circuit breaker
- Timeout por tentativa: 10s
- Timeout total: 30s

Não sobrescrever o handler padrão — apenas configurar `client.Timeout` no registro.

---

## 12. Logs obrigatórios

Usar log estruturado (chave=valor). Todos os logs devem ter `tenantId` e `empresaId` no contexto.

| Evento | Level | Campos obrigatórios |
|---|---|---|
| Início de varredura | Information | `directory`, `scanInterval` |
| Arquivo ignorado (já enviado) | Debug | `filePath`, `sha256`, `importacaoId` |
| Upload iniciado | Debug | `filePath`, `sha256`, `objectKey` |
| Upload concluído | Information | `filePath`, `sha256`, `objectKey`, `sizeBytes` |
| Upload falhou | Warning | `filePath`, `sha256`, `error` |
| Manifesto enviado | Information | `importacaoId`, `totalEntries` |
| Manifesto falhou | Error | `totalEntries`, `error` |
| Fim de iteração | Information | `found`, `skipped`, `uploaded`, `failed`, `manifestSent` |
| Agente encerrando | Information | — |

---

## 13. Testes — o que é obrigatório

Toda feature deve ter testes. Mínimo por componente:

### `FileHasherTests`
- Hash correto para arquivo de conteúdo conhecido
- Dois arquivos iguais → mesmo hash
- Arquivo inexistente → `IOException`

### `XmlFileScannerTests`
- Diretório com XMLs em subpastas → retorna todos
- Arquivo com extensão `.XML` (uppercase) → inclui
- Arquivo `.zip` → não inclui
- Diretório inexistente → `DirectoryNotFoundException`

### `SqliteUploadStateStoreTests`
- `FindUploadedAsync` retorna null para sha256 desconhecido
- `SaveAsync` persiste e `FindUploadedAsync` retorna o registro
- `GetPendingManifestAsync` retorna somente uploaded=true sem importacaoId
- `MarkManifestSentAsync` atualiza importacaoId corretamente

### `AgentWorkerTests` (usando NSubstitute)
- Arquivo já enviado com importacaoId → não faz upload, não inclui no manifesto
- Arquivo já enviado sem importacaoId → não faz upload, inclui no manifesto
- Arquivo novo → faz upload, inclui no manifesto
- Upload falha → arquivo não vai para manifesto, iteração continua
- Manifesto falha → MarkManifestSentAsync não é chamado

### `ManifestSenderTests`
- Resposta 202 → retorna `ManifestResponse` desserializado
- Resposta 400 → lança exceção sem retry
- Resposta 500 → lança `HttpRequestException`

---

## 14. Build e distribuição

```bash
# Desenvolvimento
dotnet run --project FiscalAnalyzer.Agent

# Testes
dotnet test

# Publicação Windows (single file, self-contained)
dotnet publish FiscalAnalyzer.Agent \
  -r win-x64 \
  --self-contained \
  -p:PublishSingleFile=true \
  -p:DebugType=embedded \
  -o ./publish/win-x64

# Publicação Linux
dotnet publish FiscalAnalyzer.Agent \
  -r linux-x64 \
  --self-contained \
  -p:PublishSingleFile=true \
  -o ./publish/linux-x64

# Instalar como Windows Service
sc create FiscalAgente binPath= "C:\FiscalAnalyzer.Agent\FiscalAnalyzer.Agent.exe"
sc start FiscalAgente
```

---

## 15. Regras obrigatórias

- **Não parsear XML fiscal** — nunca abrir o conteúdo do XML além do hash.
- **Não publicar no RabbitMQ** — toda comunicação é via `POST /imports/manifest`.
- **Não carregar arquivo inteiro em memória** — sempre usar stream para hash e upload.
- **Não fazer `LIST` no bucket** — a descoberta de arquivos é sempre local (varredura).
- **Chave de objeto é sempre determinística**: `{tenantId}/{empresaId}/{sha256}.xml`.
- **Trabalhar de forma incremental**: um componente por vez, testar antes de avançar.
- **`dotnet test` deve ficar verde** antes de considerar qualquer tarefa concluída.
- **Não inventar campos no contrato** da API — usar exatamente os definidos na seção 10.

## 16. Forma de trabalho

1. Ler este arquivo inteiro antes de escrever qualquer código.
2. Implementar um componente por vez (Config → State → Hashing → Scanning → Upload → Manifest → Worker).
3. Escrever os testes junto com o componente.
4. Rodar `dotnet test` e reportar resultado.
5. Só avançar para o próximo componente com testes verdes.
