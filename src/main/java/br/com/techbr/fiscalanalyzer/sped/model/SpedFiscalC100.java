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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sped_fiscal_c100")
public class SpedFiscalC100 {

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

    @Column(name = "ind_oper", length = 1)
    private String indOper;

    @Column(name = "ind_emit", length = 1)
    private String indEmit;

    @Column(name = "cod_part", length = 60)
    private String codPart;

    @Column(name = "cod_mod", length = 2)
    private String codMod;

    @Column(name = "cod_sit", length = 2)
    private String codSit;

    @Column(name = "serie", length = 10)
    private String serie;

    @Column(name = "num_doc", length = 20)
    private String numDoc;

    @Column(name = "chv_nfe", length = 44)
    private String chvNfe;

    @Column(name = "dt_doc")
    private LocalDate dtDoc;

    @Column(name = "dt_es")
    private LocalDate dtEs;

    @Column(name = "vl_doc", precision = 15, scale = 2)
    private BigDecimal vlDoc;

    @Column(name = "vl_desc", precision = 15, scale = 2)
    private BigDecimal vlDesc;

    @Column(name = "vl_merc", precision = 15, scale = 2)
    private BigDecimal vlMerc;

    @Column(name = "vl_bc_icms", precision = 15, scale = 2)
    private BigDecimal vlBcIcms;

    @Column(name = "vl_icms", precision = 15, scale = 2)
    private BigDecimal vlIcms;

    @Column(name = "vl_bc_icms_st", precision = 15, scale = 2)
    private BigDecimal vlBcIcmsSt;

    @Column(name = "vl_icms_st", precision = 15, scale = 2)
    private BigDecimal vlIcmsSt;

    @Column(name = "vl_ipi", precision = 15, scale = 2)
    private BigDecimal vlIpi;

    @Column(name = "vl_pis", precision = 15, scale = 2)
    private BigDecimal vlPis;

    @Column(name = "vl_cofins", precision = 15, scale = 2)
    private BigDecimal vlCofins;

    @Column(name = "vl_pis_st", precision = 15, scale = 2)
    private BigDecimal vlPisSt;

    @Column(name = "vl_cofins_st", precision = 15, scale = 2)
    private BigDecimal vlCofinsSt;

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

    public String getIndOper() {
        return indOper;
    }

    public void setIndOper(String indOper) {
        this.indOper = indOper;
    }

    public String getIndEmit() {
        return indEmit;
    }

    public void setIndEmit(String indEmit) {
        this.indEmit = indEmit;
    }

    public String getCodPart() {
        return codPart;
    }

    public void setCodPart(String codPart) {
        this.codPart = codPart;
    }

    public String getCodMod() {
        return codMod;
    }

    public void setCodMod(String codMod) {
        this.codMod = codMod;
    }

    public String getCodSit() {
        return codSit;
    }

    public void setCodSit(String codSit) {
        this.codSit = codSit;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public String getNumDoc() {
        return numDoc;
    }

    public void setNumDoc(String numDoc) {
        this.numDoc = numDoc;
    }

    public String getChvNfe() {
        return chvNfe;
    }

    public void setChvNfe(String chvNfe) {
        this.chvNfe = chvNfe;
    }

    public LocalDate getDtDoc() {
        return dtDoc;
    }

    public void setDtDoc(LocalDate dtDoc) {
        this.dtDoc = dtDoc;
    }

    public LocalDate getDtEs() {
        return dtEs;
    }

    public void setDtEs(LocalDate dtEs) {
        this.dtEs = dtEs;
    }

    public BigDecimal getVlDoc() {
        return vlDoc;
    }

    public void setVlDoc(BigDecimal vlDoc) {
        this.vlDoc = vlDoc;
    }

    public BigDecimal getVlDesc() {
        return vlDesc;
    }

    public void setVlDesc(BigDecimal vlDesc) {
        this.vlDesc = vlDesc;
    }

    public BigDecimal getVlMerc() {
        return vlMerc;
    }

    public void setVlMerc(BigDecimal vlMerc) {
        this.vlMerc = vlMerc;
    }

    public BigDecimal getVlBcIcms() {
        return vlBcIcms;
    }

    public void setVlBcIcms(BigDecimal vlBcIcms) {
        this.vlBcIcms = vlBcIcms;
    }

    public BigDecimal getVlIcms() {
        return vlIcms;
    }

    public void setVlIcms(BigDecimal vlIcms) {
        this.vlIcms = vlIcms;
    }

    public BigDecimal getVlBcIcmsSt() {
        return vlBcIcmsSt;
    }

    public void setVlBcIcmsSt(BigDecimal vlBcIcmsSt) {
        this.vlBcIcmsSt = vlBcIcmsSt;
    }

    public BigDecimal getVlIcmsSt() {
        return vlIcmsSt;
    }

    public void setVlIcmsSt(BigDecimal vlIcmsSt) {
        this.vlIcmsSt = vlIcmsSt;
    }

    public BigDecimal getVlIpi() {
        return vlIpi;
    }

    public void setVlIpi(BigDecimal vlIpi) {
        this.vlIpi = vlIpi;
    }

    public BigDecimal getVlPis() {
        return vlPis;
    }

    public void setVlPis(BigDecimal vlPis) {
        this.vlPis = vlPis;
    }

    public BigDecimal getVlCofins() {
        return vlCofins;
    }

    public void setVlCofins(BigDecimal vlCofins) {
        this.vlCofins = vlCofins;
    }

    public BigDecimal getVlPisSt() {
        return vlPisSt;
    }

    public void setVlPisSt(BigDecimal vlPisSt) {
        this.vlPisSt = vlPisSt;
    }

    public BigDecimal getVlCofinsSt() {
        return vlCofinsSt;
    }

    public void setVlCofinsSt(BigDecimal vlCofinsSt) {
        this.vlCofinsSt = vlCofinsSt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
