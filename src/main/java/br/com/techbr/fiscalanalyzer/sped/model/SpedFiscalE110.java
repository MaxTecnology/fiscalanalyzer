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
@Table(name = "sped_fiscal_e110")
public class SpedFiscalE110 {

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

    @Column(name = "vl_tot_debitos", precision = 15, scale = 2)
    private BigDecimal vlTotDebitos;

    @Column(name = "vl_aj_debitos", precision = 15, scale = 2)
    private BigDecimal vlAjDebitos;

    @Column(name = "vl_tot_aj_debitos", precision = 15, scale = 2)
    private BigDecimal vlTotAjDebitos;

    @Column(name = "vl_estornos_cred", precision = 15, scale = 2)
    private BigDecimal vlEstornosCred;

    @Column(name = "vl_tot_creditos", precision = 15, scale = 2)
    private BigDecimal vlTotCreditos;

    @Column(name = "vl_aj_creditos", precision = 15, scale = 2)
    private BigDecimal vlAjCreditos;

    @Column(name = "vl_tot_aj_creditos", precision = 15, scale = 2)
    private BigDecimal vlTotAjCreditos;

    @Column(name = "vl_estornos_deb", precision = 15, scale = 2)
    private BigDecimal vlEstornosDeb;

    @Column(name = "vl_sld_credor_anterior", precision = 15, scale = 2)
    private BigDecimal vlSldCredorAnterior;

    @Column(name = "vl_sld_apurado", precision = 15, scale = 2)
    private BigDecimal vlSldApurado;

    @Column(name = "vl_tot_ded", precision = 15, scale = 2)
    private BigDecimal vlTotDed;

    @Column(name = "vl_icms_recolher", precision = 15, scale = 2)
    private BigDecimal vlIcmsRecolher;

    @Column(name = "vl_sld_credor_transportar", precision = 15, scale = 2)
    private BigDecimal vlSldCredorTransportar;

    @Column(name = "deb_esp", precision = 15, scale = 2)
    private BigDecimal debEsp;

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

    public BigDecimal getVlTotDebitos() {
        return vlTotDebitos;
    }

    public void setVlTotDebitos(BigDecimal vlTotDebitos) {
        this.vlTotDebitos = vlTotDebitos;
    }

    public BigDecimal getVlAjDebitos() {
        return vlAjDebitos;
    }

    public void setVlAjDebitos(BigDecimal vlAjDebitos) {
        this.vlAjDebitos = vlAjDebitos;
    }

    public BigDecimal getVlTotAjDebitos() {
        return vlTotAjDebitos;
    }

    public void setVlTotAjDebitos(BigDecimal vlTotAjDebitos) {
        this.vlTotAjDebitos = vlTotAjDebitos;
    }

    public BigDecimal getVlEstornosCred() {
        return vlEstornosCred;
    }

    public void setVlEstornosCred(BigDecimal vlEstornosCred) {
        this.vlEstornosCred = vlEstornosCred;
    }

    public BigDecimal getVlTotCreditos() {
        return vlTotCreditos;
    }

    public void setVlTotCreditos(BigDecimal vlTotCreditos) {
        this.vlTotCreditos = vlTotCreditos;
    }

    public BigDecimal getVlAjCreditos() {
        return vlAjCreditos;
    }

    public void setVlAjCreditos(BigDecimal vlAjCreditos) {
        this.vlAjCreditos = vlAjCreditos;
    }

    public BigDecimal getVlTotAjCreditos() {
        return vlTotAjCreditos;
    }

    public void setVlTotAjCreditos(BigDecimal vlTotAjCreditos) {
        this.vlTotAjCreditos = vlTotAjCreditos;
    }

    public BigDecimal getVlEstornosDeb() {
        return vlEstornosDeb;
    }

    public void setVlEstornosDeb(BigDecimal vlEstornosDeb) {
        this.vlEstornosDeb = vlEstornosDeb;
    }

    public BigDecimal getVlSldCredorAnterior() {
        return vlSldCredorAnterior;
    }

    public void setVlSldCredorAnterior(BigDecimal vlSldCredorAnterior) {
        this.vlSldCredorAnterior = vlSldCredorAnterior;
    }

    public BigDecimal getVlSldApurado() {
        return vlSldApurado;
    }

    public void setVlSldApurado(BigDecimal vlSldApurado) {
        this.vlSldApurado = vlSldApurado;
    }

    public BigDecimal getVlTotDed() {
        return vlTotDed;
    }

    public void setVlTotDed(BigDecimal vlTotDed) {
        this.vlTotDed = vlTotDed;
    }

    public BigDecimal getVlIcmsRecolher() {
        return vlIcmsRecolher;
    }

    public void setVlIcmsRecolher(BigDecimal vlIcmsRecolher) {
        this.vlIcmsRecolher = vlIcmsRecolher;
    }

    public BigDecimal getVlSldCredorTransportar() {
        return vlSldCredorTransportar;
    }

    public void setVlSldCredorTransportar(BigDecimal vlSldCredorTransportar) {
        this.vlSldCredorTransportar = vlSldCredorTransportar;
    }

    public BigDecimal getDebEsp() {
        return debEsp;
    }

    public void setDebEsp(BigDecimal debEsp) {
        this.debEsp = debEsp;
    }
}
