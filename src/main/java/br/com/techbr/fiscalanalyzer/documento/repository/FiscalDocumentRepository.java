package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long>,
        JpaSpecificationExecutor<FiscalDocument> {
    Optional<FiscalDocument> findByTenantIdAndEmpresaIdAndAccessKey(Long tenantId, Long empresaId, String accessKey);

    long countByTenantIdAndEmpresaId(Long tenantId, Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and (d.numeroNota is null or d.serie is null or d.naturezaOperacao is null)
            """)
    long countMissingIdeCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and (d.emitNome is null or d.emitIe is null or d.emitUf is null)
            """)
    long countMissingEmitCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and (d.destDocumento is null or d.destNome is null)
            """)
    long countMissingDestCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and (d.totalFrete is null or d.totalDesconto is null or d.totalIpi is null)
            """)
    long countMissingTotalsCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and (d.protocoloNumero is null or d.protocoloStatus is null)
            """)
    long countMissingProtocolCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and not exists (select 1 from FiscalDocumentPayment p where p.document = d)
            """)
    long countWithoutPaymentCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    @Query("""
            select count(d) from FiscalDocument d
            where d.tenantId = :tenantId and d.empresaId = :empresaId
              and not exists (select 1 from FiscalDocumentAdditionalInfo ai where ai.document = d)
            """)
    long countWithoutAdditionalInfoCoverage(@Param("tenantId") Long tenantId, @Param("empresaId") Long empresaId);

    long countByTenantIdAndEmpresaIdAndIssueDateBetween(Long tenantId, Long empresaId, LocalDate issueDateFrom, LocalDate issueDateTo);

    long countByTenantIdAndEmpresaIdAndIssueDateBetweenAndModel(Long tenantId,
                                                                 Long empresaId,
                                                                 LocalDate issueDateFrom,
                                                                 LocalDate issueDateTo,
                                                                 Short model);

    long countByTenantIdAndEmpresaIdAndIssueDateBetweenAndStatusDocumento(Long tenantId,
                                                                           Long empresaId,
                                                                           LocalDate issueDateFrom,
                                                                           LocalDate issueDateTo,
                                                                           FiscalDocumentStatus statusDocumento);

    @Query("""
            select coalesce(sum(d.totalAmount), 0)
            from FiscalDocument d
            where d.tenantId = :tenantId
              and d.empresaId = :empresaId
              and d.issueDate >= :issueDateFrom
              and d.issueDate <= :issueDateTo
              and d.operationType = :operationType
            """)
    BigDecimal sumTotalAmountByOperationType(@Param("tenantId") Long tenantId,
                                             @Param("empresaId") Long empresaId,
                                             @Param("issueDateFrom") LocalDate issueDateFrom,
                                             @Param("issueDateTo") LocalDate issueDateTo,
                                             @Param("operationType") String operationType);

    @Query("""
            select coalesce(sum(d.totalIcms), 0)
            from FiscalDocument d
            where d.tenantId = :tenantId
              and d.empresaId = :empresaId
              and d.issueDate >= :issueDateFrom
              and d.issueDate <= :issueDateTo
              and d.operationType = :operationType
            """)
    BigDecimal sumTotalIcmsByOperationType(@Param("tenantId") Long tenantId,
                                           @Param("empresaId") Long empresaId,
                                           @Param("issueDateFrom") LocalDate issueDateFrom,
                                           @Param("issueDateTo") LocalDate issueDateTo,
                                           @Param("operationType") String operationType);

    @Query("""
            select distinct d.emitCnpj as cnpj, d.emitNome as razaoSocial
            from FiscalDocument d
            where d.tenantId = :tenantId
              and d.empresaId = :empresaId
              and d.emitCnpj is not null
              and (
                    :q is null
                    or lower(coalesce(d.emitNome, '')) like lower(concat('%', :q, '%'))
                    or d.emitCnpj like concat('%', :q, '%')
                  )
            order by d.emitNome asc
            """)
    List<PartyProjection> findDistinctEmitters(@Param("tenantId") Long tenantId,
                                               @Param("empresaId") Long empresaId,
                                               @Param("q") String q,
                                               Pageable pageable);

    @Query("""
            select distinct d.destCnpj as cnpj, d.destNome as razaoSocial
            from FiscalDocument d
            where d.tenantId = :tenantId
              and d.empresaId = :empresaId
              and d.destCnpj is not null
              and (
                    :q is null
                    or lower(coalesce(d.destNome, '')) like lower(concat('%', :q, '%'))
                    or d.destCnpj like concat('%', :q, '%')
                  )
            order by d.destNome asc
            """)
    List<PartyProjection> findDistinctReceivers(@Param("tenantId") Long tenantId,
                                                @Param("empresaId") Long empresaId,
                                                @Param("q") String q,
                                                Pageable pageable);

    interface PartyProjection {
        String getCnpj();
        String getRazaoSocial();
    }
}
