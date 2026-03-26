package br.com.techbr.fiscalanalyzer.documento.model;

import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fiscal_document")
public class FiscalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "model", nullable = false)
    private Short model; // 55 ou 65

    @Column(name = "access_key", nullable = false, length = 44)
    private String accessKey;

    @Column(name = "numero_nota")
    private Integer numeroNota;

    @Column(name = "serie", length = 10)
    private String serie;

    @Column(name = "natureza_operacao", columnDefinition = "text")
    private String naturezaOperacao;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "issue_datetime")
    private Instant issueDateTime;

    @Column(name = "ambiente")
    private Short ambiente;

    @Column(name = "finalidade_emissao")
    private Short finalidadeEmissao;

    @Column(name = "consumidor_final")
    private Short consumidorFinal;

    @Column(name = "presenca_comprador")
    private Short presencaComprador;

    @Column(name = "operation_type", nullable = false, length = 1)
    private String operationType; // 'E' ou 'S'

    @Column(name = "emit_cnpj", nullable = false, length = 14)
    private String emitCnpj;

    @Column(name = "emit_nome", columnDefinition = "text")
    private String emitNome;

    @Column(name = "emit_ie", length = 20)
    private String emitIe;

    @Column(name = "emit_uf", columnDefinition = "char(2)")
    private String emitUf;

    @Column(name = "dest_documento", length = 14)
    private String destDocumento;

    @Column(name = "dest_cnpj", length = 14)
    private String destCnpj;

    @Column(name = "dest_nome", columnDefinition = "text")
    private String destNome;

    @Column(name = "dest_ie", length = 20)
    private String destIe;

    @Column(name = "dest_uf", columnDefinition = "char(2)")
    private String destUf;

    @Column(name = "total_products", precision = 15, scale = 2)
    private BigDecimal totalProducts;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_frete", precision = 15, scale = 2)
    private BigDecimal totalFrete;

    @Column(name = "total_desconto", precision = 15, scale = 2)
    private BigDecimal totalDesconto;

    @Column(name = "total_outros", precision = 15, scale = 2)
    private BigDecimal totalOutros;

    @Column(name = "total_ipi", precision = 15, scale = 2)
    private BigDecimal totalIpi;

    @Column(name = "total_tributos", precision = 15, scale = 2)
    private BigDecimal totalTributos;

    @Column(name = "total_icms", precision = 15, scale = 2)
    private BigDecimal totalIcms;

    @Column(name = "total_pis", precision = 15, scale = 2)
    private BigDecimal totalPis;

    @Column(name = "total_cofins", precision = 15, scale = 2)
    private BigDecimal totalCofins;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacao_id")
    private Importacao importacao;

    @Column(name = "xml_path", nullable = false, columnDefinition = "text")
    private String xmlPath;

    @Column(name = "xml_hash", nullable = false, length = 64)
    private String xmlHash;

    @Column(name = "protocolo_numero", length = 32)
    private String protocoloNumero;

    @Column(name = "protocolo_status")
    private Integer protocoloStatus;

    @Column(name = "protocolo_motivo", columnDefinition = "text")
    private String protocoloMotivo;

    @Column(name = "protocolo_recebimento")
    private Instant protocoloRecebimento;

    @Column(name = "qr_code_url", columnDefinition = "text")
    private String qrCodeUrl;

    @Column(name = "fatura_numero", length = 60)
    private String faturaNumero;

    @Column(name = "fatura_valor_original", precision = 15, scale = 2)
    private BigDecimal faturaValorOriginal;

    @Column(name = "fatura_valor_desconto", precision = 15, scale = 2)
    private BigDecimal faturaValorDesconto;

    @Column(name = "fatura_valor_liquido", precision = 15, scale = 2)
    private BigDecimal faturaValorLiquido;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
    public Short getModel() { return model; }
    public void setModel(Short model) { this.model = model; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public Integer getNumeroNota() { return numeroNota; }
    public void setNumeroNota(Integer numeroNota) { this.numeroNota = numeroNota; }
    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }
    public String getNaturezaOperacao() { return naturezaOperacao; }
    public void setNaturezaOperacao(String naturezaOperacao) { this.naturezaOperacao = naturezaOperacao; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public Instant getIssueDateTime() { return issueDateTime; }
    public void setIssueDateTime(Instant issueDateTime) { this.issueDateTime = issueDateTime; }
    public Short getAmbiente() { return ambiente; }
    public void setAmbiente(Short ambiente) { this.ambiente = ambiente; }
    public Short getFinalidadeEmissao() { return finalidadeEmissao; }
    public void setFinalidadeEmissao(Short finalidadeEmissao) { this.finalidadeEmissao = finalidadeEmissao; }
    public Short getConsumidorFinal() { return consumidorFinal; }
    public void setConsumidorFinal(Short consumidorFinal) { this.consumidorFinal = consumidorFinal; }
    public Short getPresencaComprador() { return presencaComprador; }
    public void setPresencaComprador(Short presencaComprador) { this.presencaComprador = presencaComprador; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getEmitCnpj() { return emitCnpj; }
    public void setEmitCnpj(String emitCnpj) { this.emitCnpj = emitCnpj; }
    public String getEmitNome() { return emitNome; }
    public void setEmitNome(String emitNome) { this.emitNome = emitNome; }
    public String getEmitIe() { return emitIe; }
    public void setEmitIe(String emitIe) { this.emitIe = emitIe; }
    public String getEmitUf() { return emitUf; }
    public void setEmitUf(String emitUf) { this.emitUf = emitUf; }
    public String getDestDocumento() { return destDocumento; }
    public void setDestDocumento(String destDocumento) { this.destDocumento = destDocumento; }
    public String getDestCnpj() { return destCnpj; }
    public void setDestCnpj(String destCnpj) { this.destCnpj = destCnpj; }
    public String getDestNome() { return destNome; }
    public void setDestNome(String destNome) { this.destNome = destNome; }
    public String getDestIe() { return destIe; }
    public void setDestIe(String destIe) { this.destIe = destIe; }
    public String getDestUf() { return destUf; }
    public void setDestUf(String destUf) { this.destUf = destUf; }
    public BigDecimal getTotalProducts() { return totalProducts; }
    public void setTotalProducts(BigDecimal totalProducts) { this.totalProducts = totalProducts; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getTotalFrete() { return totalFrete; }
    public void setTotalFrete(BigDecimal totalFrete) { this.totalFrete = totalFrete; }
    public BigDecimal getTotalDesconto() { return totalDesconto; }
    public void setTotalDesconto(BigDecimal totalDesconto) { this.totalDesconto = totalDesconto; }
    public BigDecimal getTotalOutros() { return totalOutros; }
    public void setTotalOutros(BigDecimal totalOutros) { this.totalOutros = totalOutros; }
    public BigDecimal getTotalIpi() { return totalIpi; }
    public void setTotalIpi(BigDecimal totalIpi) { this.totalIpi = totalIpi; }
    public BigDecimal getTotalTributos() { return totalTributos; }
    public void setTotalTributos(BigDecimal totalTributos) { this.totalTributos = totalTributos; }
    public BigDecimal getTotalIcms() { return totalIcms; }
    public void setTotalIcms(BigDecimal totalIcms) { this.totalIcms = totalIcms; }
    public BigDecimal getTotalPis() { return totalPis; }
    public void setTotalPis(BigDecimal totalPis) { this.totalPis = totalPis; }
    public BigDecimal getTotalCofins() { return totalCofins; }
    public void setTotalCofins(BigDecimal totalCofins) { this.totalCofins = totalCofins; }
    public Importacao getImportacao() { return importacao; }
    public void setImportacao(Importacao importacao) { this.importacao = importacao; }
    public String getXmlPath() { return xmlPath; }
    public void setXmlPath(String xmlPath) { this.xmlPath = xmlPath; }
    public String getXmlHash() { return xmlHash; }
    public void setXmlHash(String xmlHash) { this.xmlHash = xmlHash; }
    public String getProtocoloNumero() { return protocoloNumero; }
    public void setProtocoloNumero(String protocoloNumero) { this.protocoloNumero = protocoloNumero; }
    public Integer getProtocoloStatus() { return protocoloStatus; }
    public void setProtocoloStatus(Integer protocoloStatus) { this.protocoloStatus = protocoloStatus; }
    public String getProtocoloMotivo() { return protocoloMotivo; }
    public void setProtocoloMotivo(String protocoloMotivo) { this.protocoloMotivo = protocoloMotivo; }
    public Instant getProtocoloRecebimento() { return protocoloRecebimento; }
    public void setProtocoloRecebimento(Instant protocoloRecebimento) { this.protocoloRecebimento = protocoloRecebimento; }
    public String getQrCodeUrl() { return qrCodeUrl; }
    public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }
    public String getFaturaNumero() { return faturaNumero; }
    public void setFaturaNumero(String faturaNumero) { this.faturaNumero = faturaNumero; }
    public BigDecimal getFaturaValorOriginal() { return faturaValorOriginal; }
    public void setFaturaValorOriginal(BigDecimal faturaValorOriginal) { this.faturaValorOriginal = faturaValorOriginal; }
    public BigDecimal getFaturaValorDesconto() { return faturaValorDesconto; }
    public void setFaturaValorDesconto(BigDecimal faturaValorDesconto) { this.faturaValorDesconto = faturaValorDesconto; }
    public BigDecimal getFaturaValorLiquido() { return faturaValorLiquido; }
    public void setFaturaValorLiquido(BigDecimal faturaValorLiquido) { this.faturaValorLiquido = faturaValorLiquido; }
    public Instant getCreatedAt() { return createdAt; }
}
