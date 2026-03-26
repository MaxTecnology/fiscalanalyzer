package br.com.techbr.fiscalanalyzer.documento.repository;

import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {
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
}
