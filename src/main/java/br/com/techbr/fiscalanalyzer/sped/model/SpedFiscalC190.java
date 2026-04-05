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

@Entity
@Table(name = "sped_fiscal_c190")
public class SpedFiscalC190 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "c100_id", nullable = false)
    private SpedFiscalC100 c100;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "linha_origem", nullable = false)
    private Integer linhaOrigem;

    @Column(name = "cst_icms", length = 3)
    private String cstIcms;

    @Column(name = "cfop", length = 4)
    private String cfop;

    @Column(name = "aliq_icms", precision = 7, scale = 4)
    private BigDecimal aliqIcms;

    @Column(name = "vl_opr", precision = 15, scale = 2)
    private BigDecimal vlOpr;

    @Column(name = "vl_bc_icms", precision = 15, scale = 2)
    private BigDecimal vlBcIcms;

    @Column(name = "vl_icms", precision = 15, scale = 2)
    private BigDecimal vlIcms;

    @Column(name = "vl_bc_icms_st", precision = 15, scale = 2)
    private BigDecimal vlBcIcmsSt;

    @Column(name = "vl_icms_st", precision = 15, scale = 2)
    private BigDecimal vlIcmsSt;

    @Column(name = "vl_red_bc", precision = 15, scale = 2)
    private BigDecimal vlRedBc;

    @Column(name = "vl_ipi", precision = 15, scale = 2)
    private BigDecimal vlIpi;

    @Column(name = "cod_obs", length = 10)
    private String codObs;

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

    public SpedFiscalC100 getC100() {
        return c100;
    }

    public void setC100(SpedFiscalC100 c100) {
        this.c100 = c100;
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

    public String getCstIcms() {
        return cstIcms;
    }

    public void setCstIcms(String cstIcms) {
        this.cstIcms = cstIcms;
    }

    public String getCfop() {
        return cfop;
    }

    public void setCfop(String cfop) {
        this.cfop = cfop;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getVlOpr() {
        return vlOpr;
    }

    public void setVlOpr(BigDecimal vlOpr) {
        this.vlOpr = vlOpr;
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

    public BigDecimal getVlRedBc() {
        return vlRedBc;
    }

    public void setVlRedBc(BigDecimal vlRedBc) {
        this.vlRedBc = vlRedBc;
    }

    public BigDecimal getVlIpi() {
        return vlIpi;
    }

    public void setVlIpi(BigDecimal vlIpi) {
        this.vlIpi = vlIpi;
    }

    public String getCodObs() {
        return codObs;
    }

    public void setCodObs(String codObs) {
        this.codObs = codObs;
    }
}
