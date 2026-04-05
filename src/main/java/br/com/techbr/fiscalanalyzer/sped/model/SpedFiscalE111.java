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
@Table(name = "sped_fiscal_e111")
public class SpedFiscalE111 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "e110_id", nullable = false)
    private SpedFiscalE110 e110;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "linha_origem", nullable = false)
    private Integer linhaOrigem;

    @Column(name = "cod_aj_apur", length = 20)
    private String codAjApur;

    @Column(name = "descr_compl_aj", columnDefinition = "text")
    private String descrComplAj;

    @Column(name = "vl_aj_apur", precision = 15, scale = 2)
    private BigDecimal vlAjApur;

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

    public SpedFiscalE110 getE110() {
        return e110;
    }

    public void setE110(SpedFiscalE110 e110) {
        this.e110 = e110;
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

    public String getCodAjApur() {
        return codAjApur;
    }

    public void setCodAjApur(String codAjApur) {
        this.codAjApur = codAjApur;
    }

    public String getDescrComplAj() {
        return descrComplAj;
    }

    public void setDescrComplAj(String descrComplAj) {
        this.descrComplAj = descrComplAj;
    }

    public BigDecimal getVlAjApur() {
        return vlAjApur;
    }

    public void setVlAjApur(BigDecimal vlAjApur) {
        this.vlAjApur = vlAjApur;
    }
}
