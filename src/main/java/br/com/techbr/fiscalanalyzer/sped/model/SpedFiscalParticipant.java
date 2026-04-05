package br.com.techbr.fiscalanalyzer.sped.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sped_fiscal_participant")
public class SpedFiscalParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private SpedFiscalFile file;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "linha_origem", nullable = false)
    private Integer linhaOrigem;

    @Column(name = "cod_part", nullable = false, length = 60)
    private String codPart;

    @Column(name = "nome", columnDefinition = "text")
    private String nome;

    @Column(name = "cod_pais", length = 5)
    private String codPais;

    @Column(name = "cnpj", length = 14)
    private String cnpj;

    @Column(name = "cpf", length = 11)
    private String cpf;

    @Column(name = "ie", length = 20)
    private String ie;

    @Column(name = "cod_mun", length = 7)
    private String codMun;

    @Column(name = "suframa", length = 20)
    private String suframa;

    @Column(name = "endereco", columnDefinition = "text")
    private String endereco;

    @Column(name = "numero", length = 60)
    private String numero;

    @Column(name = "complemento", columnDefinition = "text")
    private String complemento;

    @Column(name = "bairro", columnDefinition = "text")
    private String bairro;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public SpedFiscalFile getFile() {
        return file;
    }

    public void setFile(SpedFiscalFile file) {
        this.file = file;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Long empresaId) {
        this.empresaId = empresaId;
    }

    public Integer getLinhaOrigem() {
        return linhaOrigem;
    }

    public void setLinhaOrigem(Integer linhaOrigem) {
        this.linhaOrigem = linhaOrigem;
    }

    public String getCodPart() {
        return codPart;
    }

    public void setCodPart(String codPart) {
        this.codPart = codPart;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodPais() {
        return codPais;
    }

    public void setCodPais(String codPais) {
        this.codPais = codPais;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getIe() {
        return ie;
    }

    public void setIe(String ie) {
        this.ie = ie;
    }

    public String getCodMun() {
        return codMun;
    }

    public void setCodMun(String codMun) {
        this.codMun = codMun;
    }

    public String getSuframa() {
        return suframa;
    }

    public void setSuframa(String suframa) {
        this.suframa = suframa;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }
}
