package br.com.techbr.fiscalanalyzer.importacao.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_item")
public class ImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false)
    private Importacao importacao;

    @Column(name = "xml_path", nullable = false, columnDefinition = "text")
    private String xmlPath;

    @Column(name = "xml_hash", length = 64)
    private String xmlHash;

    @Column(name = "xml_size")
    private Long xmlSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportItemStatus status;

    @Column(name = "erro_codigo", length = 50)
    private String erroCodigo;

    @Column(name = "erro_mensagem", columnDefinition = "text")
    private String erroMensagem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters/setters
    public Long getId() { return id; }
    public Importacao getImportacao() { return importacao; }
    public void setImportacao(Importacao importacao) { this.importacao = importacao; }
    public String getXmlPath() { return xmlPath; }
    public void setXmlPath(String xmlPath) { this.xmlPath = xmlPath; }
    public String getXmlHash() { return xmlHash; }
    public void setXmlHash(String xmlHash) { this.xmlHash = xmlHash; }
    public Long getXmlSize() { return xmlSize; }
    public void setXmlSize(Long xmlSize) { this.xmlSize = xmlSize; }
    public ImportItemStatus getStatus() { return status; }
    public void setStatus(ImportItemStatus status) { this.status = status; }
    public String getErroCodigo() { return erroCodigo; }
    public void setErroCodigo(String erroCodigo) { this.erroCodigo = erroCodigo; }
    public String getErroMensagem() { return erroMensagem; }
    public void setErroMensagem(String erroMensagem) { this.erroMensagem = erroMensagem; }
    public Instant getCreatedAt() { return createdAt; }
}
