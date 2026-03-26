package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageBackfillResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentAdditionalInfoRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentDuplicateRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentPaymentRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCoverageServiceTest {

    @Mock
    private ImportItemRepository importItemRepository;
    @Mock
    private FiscalDocumentRepository fiscalDocumentRepository;
    @Mock
    private FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository;
    @Mock
    private FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository;
    @Mock
    private FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository;
    @Mock
    private TenantEmpresaValidationService tenantEmpresaValidationService;
    @Mock
    private StorageService storageService;

    @Test
    void summarize_retornaContadores() {
        DocumentCoverageService service = new DocumentCoverageService(
                importItemRepository,
                fiscalDocumentRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                tenantEmpresaValidationService,
                storageService,
                new SimpleMeterRegistry()
        );

        when(fiscalDocumentRepository.countByTenantIdAndEmpresaId(1L, 2L)).thenReturn(100L);
        when(fiscalDocumentRepository.countMissingIdeCoverage(1L, 2L)).thenReturn(20L);
        when(fiscalDocumentRepository.countMissingEmitCoverage(1L, 2L)).thenReturn(10L);
        when(fiscalDocumentRepository.countMissingDestCoverage(1L, 2L)).thenReturn(15L);
        when(fiscalDocumentRepository.countMissingTotalsCoverage(1L, 2L)).thenReturn(30L);
        when(fiscalDocumentRepository.countMissingProtocolCoverage(1L, 2L)).thenReturn(25L);
        when(fiscalDocumentRepository.countWithoutPaymentCoverage(1L, 2L)).thenReturn(40L);
        when(fiscalDocumentRepository.countWithoutAdditionalInfoCoverage(1L, 2L)).thenReturn(50L);

        DocumentCoverageSummaryResponse response = service.summarize(1L, 2L);

        assertEquals(100L, response.totalDocuments());
        assertEquals(20L, response.missingIdeCoverage());
        assertEquals(40L, response.documentsWithoutPayments());
    }

    @Test
    void backfill_objetoDireto_atualizaDocumentoEPersistenciasFilhas() {
        DocumentCoverageService service = new DocumentCoverageService(
                importItemRepository,
                fiscalDocumentRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                tenantEmpresaValidationService,
                storageService,
                new SimpleMeterRegistry()
        );

        ImportItemRepository.CoverageBackfillCandidateProjection candidate = new ImportItemRepository.CoverageBackfillCandidateProjection() {
            @Override
            public Long getImportItemId() { return 101L; }

            @Override
            public String getAccessKey() { return "26251052729931000390650060000031721472939584"; }

            @Override
            public String getXmlPath() { return "1/2/nfce.xml"; }

            @Override
            public String getStorageObjectKey() { return "1/2/nfce.xml"; }

            @Override
            public String getArquivoPath() { return null; }

            @Override
            public ImportacaoSourceType getSourceType() { return ImportacaoSourceType.MANIFEST; }
        };

        FiscalDocument doc = new FiscalDocument();
        ReflectionTestUtils.setField(doc, "id", 900L);
        doc.setTenantId(1L);
        doc.setEmpresaId(2L);
        doc.setAccessKey(candidate.getAccessKey());

        when(importItemRepository.findCoverageBackfillCandidates(anyLong(), anyLong(), anySet(), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(1L, 2L, candidate.getAccessKey()))
                .thenReturn(Optional.of(doc));
        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(storageService.get("1/2/nfce.xml"))
                .thenReturn(new ByteArrayInputStream(nfceXml().getBytes(StandardCharsets.UTF_8)));
        when(fiscalDocumentPaymentRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(fiscalDocumentAdditionalInfoRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        DocumentCoverageBackfillResponse response = service.backfill(1L, 2L, 100);

        assertEquals(1, response.processedItems());
        assertEquals(1, response.updatedDocuments());
        assertEquals(0, response.failedItems());
        assertEquals("CONSUMIDOR FINAL", doc.getDestNome());
        assertEquals("4.07", doc.getTotalAmount().toString());
        assertEquals("226250870102596", doc.getProtocoloNumero());

        verify(fiscalDocumentPaymentRepository).deleteByDocumentId(900L);
        verify(fiscalDocumentDuplicateRepository).deleteByDocumentId(900L);
        verify(fiscalDocumentAdditionalInfoRepository).deleteByDocumentId(900L);
    }

    private String nfceXml() {
        return """
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
                  <NFe>
                    <infNFe Id="NFe26251052729931000390650060000031721472939584">
                      <ide>
                        <natOp>VENDA DE MERC. ADQUIRIDA OU RECEBIDA DE TERCEIROS</natOp>
                        <mod>65</mod>
                        <serie>6</serie>
                        <nNF>3172</nNF>
                        <dhEmi>2025-10-01T08:18:08-03:00</dhEmi>
                        <tpNF>1</tpNF>
                        <tpAmb>1</tpAmb>
                        <finNFe>1</finNFe>
                        <indFinal>1</indFinal>
                        <indPres>1</indPres>
                      </ide>
                      <emit><CNPJ>52729931000390</CNPJ><xNome>REAL DISTR E COMERCIO DE ALIMENTOS LTDA</xNome><IE>125655711</IE><enderEmit><UF>PE</UF></enderEmit></emit>
                      <dest><CPF>23168800023</CPF><xNome>CONSUMIDOR FINAL</xNome></dest>
                      <total><ICMSTot><vProd>4.07</vProd><vFrete>0.00</vFrete><vDesc>0.00</vDesc><vIPI>0.00</vIPI><vICMS>0.49</vICMS><vPIS>0.03</vPIS><vCOFINS>0.14</vCOFINS><vNF>4.07</vNF><vTotTrib>1.41</vTotTrib></ICMSTot></total>
                      <pag><detPag><tPag>04</tPag><vPag>4.07</vPag></detPag></pag>
                      <infAdic><obsCont xCampo="OBSCONT"><xTexto>OBS</xTexto></obsCont></infAdic>
                    </infNFe>
                    <infNFeSupl><qrCode>http://nfce.sefaz.pe.gov.br/nfce-web/consultarNFCe</qrCode></infNFeSupl>
                  </NFe>
                  <protNFe><infProt><nProt>226250870102596</nProt><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo><dhRecbto>2025-10-01T08:18:09-03:00</dhRecbto></infProt></protNFe>
                </nfeProc>
                """;
    }
}
