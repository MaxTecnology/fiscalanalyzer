package br.com.techbr.fiscalanalyzer.importacao.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "importacao")
public class Importacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportacaoStatus status;

    @Column(name = "arquivo_nome", nullable = false, length = 255)
    private String arquivoNome;

    @Column(name = "arquivo_path", nullable = false, columnDefinition = "text")
    private String arquivoPath;

    @Column(name = "arquivo_hash", nullable = false, length = 64)
    private String arquivoHash;

    @Column(name = "arquivo_tamanho")
    private Long arquivoTamanho;

    @Column(name = "arquivo_content_type", length = 100)
    private String arquivoContentType;

    @Column(name = "total_encontrado", nullable = false)
    private Integer totalEncontrado = 0;

    @Column(name = "total_processado", nullable = false)
    private Integer totalProcessado = 0;

    @Column(name = "total_erros", nullable = false)
    private Integer totalErros = 0;

    @Column(name = "erro_codigo", length = 50)
    private String erroCodigo;

    @Column(name = "erro_mensagem", columnDefinition = "text")
    private String erroMensagem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters/setters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }
    public ImportacaoStatus getStatus() { return status; }
    public void setStatus(ImportacaoStatus status) { this.status = status; }
    public String getArquivoNome() { return arquivoNome; }
    public void setArquivoNome(String arquivoNome) { this.arquivoNome = arquivoNome; }
    public String getArquivoPath() { return arquivoPath; }
    public void setArquivoPath(String arquivoPath) { this.arquivoPath = arquivoPath; }
    public String getArquivoHash() { return arquivoHash; }
    public void setArquivoHash(String arquivoHash) { this.arquivoHash = arquivoHash; }
    public Long getArquivoTamanho() { return arquivoTamanho; }
    public void setArquivoTamanho(Long arquivoTamanho) { this.arquivoTamanho = arquivoTamanho; }
    public String getArquivoContentType() { return arquivoContentType; }
    public void setArquivoContentType(String arquivoContentType) { this.arquivoContentType = arquivoContentType; }
    public Integer getTotalEncontrado() { return totalEncontrado; }
    public void setTotalEncontrado(Integer totalEncontrado) { this.totalEncontrado = totalEncontrado; }
    public Integer getTotalProcessado() { return totalProcessado; }
    public void setTotalProcessado(Integer totalProcessado) { this.totalProcessado = totalProcessado; }
    public Integer getTotalErros() { return totalErros; }
    public void setTotalErros(Integer totalErros) { this.totalErros = totalErros; }
    public String getErroCodigo() { return erroCodigo; }
    public void setErroCodigo(String erroCodigo) { this.erroCodigo = erroCodigo; }
    public String getErroMensagem() { return erroMensagem; }
    public void setErroMensagem(String erroMensagem) { this.erroMensagem = erroMensagem; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
