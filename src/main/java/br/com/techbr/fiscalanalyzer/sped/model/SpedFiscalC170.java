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
@Table(name = "sped_fiscal_c170")
public class SpedFiscalC170 {

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

    @Column(name = "num_item")
    private Integer numItem;

    @Column(name = "cod_item", length = 60)
    private String codItem;

    @Column(name = "descr_compl", columnDefinition = "text")
    private String descrCompl;

    @Column(name = "qtd", precision = 15, scale = 4)
    private BigDecimal qtd;

    @Column(name = "unid", length = 10)
    private String unid;

    @Column(name = "vl_item", precision = 15, scale = 2)
    private BigDecimal vlItem;

    @Column(name = "vl_desc", precision = 15, scale = 2)
    private BigDecimal vlDesc;

    @Column(name = "cst_icms", length = 3)
    private String cstIcms;

    @Column(name = "cfop", length = 4)
    private String cfop;

    @Column(name = "vl_bc_icms", precision = 15, scale = 2)
    private BigDecimal vlBcIcms;

    @Column(name = "aliq_icms", precision = 7, scale = 4)
    private BigDecimal aliqIcms;

    @Column(name = "vl_icms", precision = 15, scale = 2)
    private BigDecimal vlIcms;

    @Column(name = "cst_ipi", length = 3)
    private String cstIpi;

    @Column(name = "vl_bc_ipi", precision = 15, scale = 2)
    private BigDecimal vlBcIpi;

    @Column(name = "aliq_ipi", precision = 7, scale = 4)
    private BigDecimal aliqIpi;

    @Column(name = "vl_ipi", precision = 15, scale = 2)
    private BigDecimal vlIpi;

    @Column(name = "cst_pis", length = 3)
    private String cstPis;

    @Column(name = "vl_bc_pis", precision = 15, scale = 2)
    private BigDecimal vlBcPis;

    @Column(name = "aliq_pis", precision = 7, scale = 4)
    private BigDecimal aliqPis;

    @Column(name = "vl_pis", precision = 15, scale = 2)
    private BigDecimal vlPis;

    @Column(name = "cst_cofins", length = 3)
    private String cstCofins;

    @Column(name = "vl_bc_cofins", precision = 15, scale = 2)
    private BigDecimal vlBcCofins;

    @Column(name = "aliq_cofins", precision = 7, scale = 4)
    private BigDecimal aliqCofins;

    @Column(name = "vl_cofins", precision = 15, scale = 2)
    private BigDecimal vlCofins;

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

    public Integer getNumItem() {
        return numItem;
    }

    public void setNumItem(Integer numItem) {
        this.numItem = numItem;
    }

    public String getCodItem() {
        return codItem;
    }

    public void setCodItem(String codItem) {
        this.codItem = codItem;
    }

    public String getDescrCompl() {
        return descrCompl;
    }

    public void setDescrCompl(String descrCompl) {
        this.descrCompl = descrCompl;
    }

    public BigDecimal getQtd() {
        return qtd;
    }

    public void setQtd(BigDecimal qtd) {
        this.qtd = qtd;
    }

    public String getUnid() {
        return unid;
    }

    public void setUnid(String unid) {
        this.unid = unid;
    }

    public BigDecimal getVlItem() {
        return vlItem;
    }

    public void setVlItem(BigDecimal vlItem) {
        this.vlItem = vlItem;
    }

    public BigDecimal getVlDesc() {
        return vlDesc;
    }

    public void setVlDesc(BigDecimal vlDesc) {
        this.vlDesc = vlDesc;
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

    public BigDecimal getVlBcIcms() {
        return vlBcIcms;
    }

    public void setVlBcIcms(BigDecimal vlBcIcms) {
        this.vlBcIcms = vlBcIcms;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getVlIcms() {
        return vlIcms;
    }

    public void setVlIcms(BigDecimal vlIcms) {
        this.vlIcms = vlIcms;
    }

    public String getCstIpi() {
        return cstIpi;
    }

    public void setCstIpi(String cstIpi) {
        this.cstIpi = cstIpi;
    }

    public BigDecimal getVlBcIpi() {
        return vlBcIpi;
    }

    public void setVlBcIpi(BigDecimal vlBcIpi) {
        this.vlBcIpi = vlBcIpi;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public BigDecimal getVlIpi() {
        return vlIpi;
    }

    public void setVlIpi(BigDecimal vlIpi) {
        this.vlIpi = vlIpi;
    }

    public String getCstPis() {
        return cstPis;
    }

    public void setCstPis(String cstPis) {
        this.cstPis = cstPis;
    }

    public BigDecimal getVlBcPis() {
        return vlBcPis;
    }

    public void setVlBcPis(BigDecimal vlBcPis) {
        this.vlBcPis = vlBcPis;
    }

    public BigDecimal getAliqPis() {
        return aliqPis;
    }

    public void setAliqPis(BigDecimal aliqPis) {
        this.aliqPis = aliqPis;
    }

    public BigDecimal getVlPis() {
        return vlPis;
    }

    public void setVlPis(BigDecimal vlPis) {
        this.vlPis = vlPis;
    }

    public String getCstCofins() {
        return cstCofins;
    }

    public void setCstCofins(String cstCofins) {
        this.cstCofins = cstCofins;
    }

    public BigDecimal getVlBcCofins() {
        return vlBcCofins;
    }

    public void setVlBcCofins(BigDecimal vlBcCofins) {
        this.vlBcCofins = vlBcCofins;
    }

    public BigDecimal getAliqCofins() {
        return aliqCofins;
    }

    public void setAliqCofins(BigDecimal aliqCofins) {
        this.aliqCofins = aliqCofins;
    }

    public BigDecimal getVlCofins() {
        return vlCofins;
    }

    public void setVlCofins(BigDecimal vlCofins) {
        this.vlCofins = vlCofins;
    }
}
