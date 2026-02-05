package br.com.techbr.fiscalanalyzer.item.model;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "fiscal_item")
public class FiscalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private FiscalDocument document;

    @Column(name = "item_number", nullable = false)
    private Integer itemNumber;

    @Column(name = "product_code", length = 60)
    private String productCode;

    @Column(name = "product_description", columnDefinition = "text")
    private String productDescription;

    @Column(name = "ncm", length = 8)
    private String ncm;

    @Column(name = "cfop", length = 4)
    private String cfop;

    @Column(name = "cst_icms", length = 3)
    private String cstIcms;

    @Column(name = "csosn", length = 3)
    private String csosn;

    @Column(name = "quantity", precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "total_value", precision = 15, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "icms_base", precision = 15, scale = 2)
    private BigDecimal icmsBase;

    @Column(name = "icms_rate", precision = 5, scale = 2)
    private BigDecimal icmsRate;

    @Column(name = "icms_value", precision = 15, scale = 2)
    private BigDecimal icmsValue;

    @Column(name = "pis_base", precision = 15, scale = 2)
    private BigDecimal pisBase;

    @Column(name = "pis_rate", precision = 5, scale = 2)
    private BigDecimal pisRate;

    @Column(name = "pis_value", precision = 15, scale = 2)
    private BigDecimal pisValue;

    @Column(name = "cofins_base", precision = 15, scale = 2)
    private BigDecimal cofinsBase;

    @Column(name = "cofins_rate", precision = 5, scale = 2)
    private BigDecimal cofinsRate;

    @Column(name = "cofins_value", precision = 15, scale = 2)
    private BigDecimal cofinsValue;

    // getters/setters
    public Long getId() { return id; }
    public FiscalDocument getDocument() { return document; }
    public void setDocument(FiscalDocument document) { this.document = document; }
    public Integer getItemNumber() { return itemNumber; }
    public void setItemNumber(Integer itemNumber) { this.itemNumber = itemNumber; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductDescription() { return productDescription; }
    public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }
    public String getCfop() { return cfop; }
    public void setCfop(String cfop) { this.cfop = cfop; }
    public String getCstIcms() { return cstIcms; }
    public void setCstIcms(String cstIcms) { this.cstIcms = cstIcms; }
    public String getCsosn() { return csosn; }
    public void setCsosn(String csosn) { this.csosn = csosn; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public BigDecimal getIcmsBase() { return icmsBase; }
    public void setIcmsBase(BigDecimal icmsBase) { this.icmsBase = icmsBase; }
    public BigDecimal getIcmsRate() { return icmsRate; }
    public void setIcmsRate(BigDecimal icmsRate) { this.icmsRate = icmsRate; }
    public BigDecimal getIcmsValue() { return icmsValue; }
    public void setIcmsValue(BigDecimal icmsValue) { this.icmsValue = icmsValue; }
    public BigDecimal getPisBase() { return pisBase; }
    public void setPisBase(BigDecimal pisBase) { this.pisBase = pisBase; }
    public BigDecimal getPisRate() { return pisRate; }
    public void setPisRate(BigDecimal pisRate) { this.pisRate = pisRate; }
    public BigDecimal getPisValue() { return pisValue; }
    public void setPisValue(BigDecimal pisValue) { this.pisValue = pisValue; }
    public BigDecimal getCofinsBase() { return cofinsBase; }
    public void setCofinsBase(BigDecimal cofinsBase) { this.cofinsBase = cofinsBase; }
    public BigDecimal getCofinsRate() { return cofinsRate; }
    public void setCofinsRate(BigDecimal cofinsRate) { this.cofinsRate = cofinsRate; }
    public BigDecimal getCofinsValue() { return cofinsValue; }
    public void setCofinsValue(BigDecimal cofinsValue) { this.cofinsValue = cofinsValue; }
}
