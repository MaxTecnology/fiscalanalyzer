package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC100;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileSourceType;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.model.SpedReconciliationStatus;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC170Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC190Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC100Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE110Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE111Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpedFiscalReadServiceTest {

    private SpedFiscalFileRepository repository;
    private SpedFiscalC100Repository spedFiscalC100Repository;
    private SpedFiscalC170Repository spedFiscalC170Repository;
    private SpedFiscalC190Repository spedFiscalC190Repository;
    private SpedFiscalE110Repository spedFiscalE110Repository;
    private SpedFiscalE111Repository spedFiscalE111Repository;
    private FiscalDocumentRepository fiscalDocumentRepository;
    private FiscalItemRepository fiscalItemRepository;
    private UserAuthorizationService userAuthorizationService;
    private ApplicationEventPublisher eventPublisher;
    private SpedFiscalReadService service;
    private UserAuthContext auth;

    @BeforeEach
    void setUp() {
        repository = mock(SpedFiscalFileRepository.class);
        spedFiscalC100Repository = mock(SpedFiscalC100Repository.class);
        spedFiscalC170Repository = mock(SpedFiscalC170Repository.class);
        spedFiscalC190Repository = mock(SpedFiscalC190Repository.class);
        spedFiscalE110Repository = mock(SpedFiscalE110Repository.class);
        spedFiscalE111Repository = mock(SpedFiscalE111Repository.class);
        fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        fiscalItemRepository = mock(FiscalItemRepository.class);
        userAuthorizationService = mock(UserAuthorizationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SpedFiscalReadService(
                repository,
                spedFiscalC100Repository,
                spedFiscalC170Repository,
                spedFiscalC190Repository,
                spedFiscalE110Repository,
                spedFiscalE111Repository,
                fiscalDocumentRepository,
                fiscalItemRepository,
                userAuthorizationService,
                eventPublisher,
                "fiscal-analyzer"
        );
        auth = new UserAuthContext(1L, "admin@techbr.com", List.of("ADMIN"));
        doNothing().when(userAuthorizationService).assertCanRead(any(), any(), any());
        doNothing().when(userAuthorizationService).assertCanWrite(any(), any(), any());
    }

    @Test
    void list_com_status_retorna_pagina() {
        SpedFiscalFile file = new SpedFiscalFile();
        file.setTenantId(1L);
        file.setEmpresaId(2L);
        file.setStatus(SpedFiscalFileStatus.PROCESSADO);
        file.setSourceType(SpedFiscalFileSourceType.TXT);
        file.setOriginalFileName("arquivo.txt");
        file.setHashSha256("hash");
        when(repository.findByTenantIdAndEmpresaIdAndStatus(eq(1L), eq(2L), eq(SpedFiscalFileStatus.PROCESSADO), any()))
                .thenReturn(new PageImpl<>(List.of(file), PageRequest.of(0, 20), 1));

        var page = service.list(1L, 2L, SpedFiscalFileStatus.PROCESSADO, PageRequest.of(0, 20), auth);

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void reprocess_publica_evento() {
        SpedFiscalFile file = new SpedFiscalFile();
        file.setTenantId(1L);
        file.setEmpresaId(2L);
        file.setStatus(SpedFiscalFileStatus.FALHA);
        file.setSourceType(SpedFiscalFileSourceType.TXT);
        file.setOriginalFileName("arquivo.txt");
        file.setStorageObjectKey("sped/1/2/hash.txt");
        file.setHashSha256("hash");
        setId(file, 10L);

        when(repository.findByIdAndTenantIdAndEmpresaId(10L, 1L, 2L)).thenReturn(Optional.of(file));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reprocess(10L, 1L, 2L, auth);

        assertEquals(10L, response.id());
        assertEquals(SpedFiscalFileStatus.PENDENTE_PARSE, file.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void detail_inexistente_lanca_422() {
        when(repository.findByIdAndTenantIdAndEmpresaId(99L, 1L, 2L)).thenReturn(Optional.empty());
        assertThrows(UnprocessableEntityException.class, () -> service.detail(99L, 1L, 2L, auth));
    }

    @Test
    void reconciliation_classifica_ok_e_faltante() {
        SpedFiscalFile file = buildSpedFile(30L);
        when(repository.findByIdAndTenantIdAndEmpresaId(30L, 1L, 2L)).thenReturn(Optional.of(file));

        SpedFiscalC100 c100Ok = new SpedFiscalC100();
        setId(c100Ok, 100L, SpedFiscalC100.class);
        c100Ok.setFile(file);
        c100Ok.setTenantId(1L);
        c100Ok.setEmpresaId(2L);
        c100Ok.setLinhaOrigem(10);
        c100Ok.setChvNfe("35191111111111111111550010000000011000000010");
        c100Ok.setVlDoc(new java.math.BigDecimal("100.00"));
        c100Ok.setCodMod("55");
        c100Ok.setNumDoc("123");
        c100Ok.setSerie("1");

        SpedFiscalC100 c100Missing = new SpedFiscalC100();
        setId(c100Missing, 101L, SpedFiscalC100.class);
        c100Missing.setFile(file);
        c100Missing.setTenantId(1L);
        c100Missing.setEmpresaId(2L);
        c100Missing.setLinhaOrigem(11);
        c100Missing.setChvNfe("35191111111111111111550010000000011000000099");
        c100Missing.setVlDoc(new java.math.BigDecimal("200.00"));
        c100Missing.setCodMod("55");
        c100Missing.setNumDoc("124");
        c100Missing.setSerie("1");

        when(spedFiscalC100Repository.findByFileIdOrderByLinhaOrigemAsc(eq(30L), any()))
                .thenReturn(new PageImpl<>(List.of(c100Ok, c100Missing), PageRequest.of(0, 50), 2));
        when(spedFiscalC170Repository.countByC100Ids(any())).thenReturn(List.of());
        when(spedFiscalC190Repository.sumIcmsByC100Ids(any())).thenReturn(List.of());
        when(fiscalItemRepository.summarizeByDocumentIds(any())).thenReturn(List.of());

        FiscalDocument doc = new FiscalDocument();
        setId(doc, 501L, FiscalDocument.class);
        doc.setTenantId(1L);
        doc.setEmpresaId(2L);
        doc.setAccessKey("35191111111111111111550010000000011000000010");
        doc.setTotalAmount(new java.math.BigDecimal("100.00"));
        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKeyIn(eq(1L), eq(2L), any()))
                .thenReturn(List.of(doc));

        var page = service.reconciliation(30L, 1L, 2L, PageRequest.of(0, 50), auth);

        assertEquals(2, page.getTotalElements());
        assertEquals(SpedReconciliationStatus.OK, page.getContent().get(0).status());
        assertEquals(SpedReconciliationStatus.FALTANTE_XML, page.getContent().get(1).status());
    }

    @Test
    void reconciliation_classifica_item_e_imposto_divergente() {
        SpedFiscalFile file = buildSpedFile(31L);
        when(repository.findByIdAndTenantIdAndEmpresaId(31L, 1L, 2L)).thenReturn(Optional.of(file));

        SpedFiscalC100 c100ItemDiff = new SpedFiscalC100();
        setId(c100ItemDiff, 200L, SpedFiscalC100.class);
        c100ItemDiff.setFile(file);
        c100ItemDiff.setTenantId(1L);
        c100ItemDiff.setEmpresaId(2L);
        c100ItemDiff.setLinhaOrigem(20);
        c100ItemDiff.setChvNfe("35191111111111111111550010000000011000000020");
        c100ItemDiff.setVlDoc(new java.math.BigDecimal("100.00"));
        c100ItemDiff.setVlIcms(new java.math.BigDecimal("12.00"));
        c100ItemDiff.setVlPis(new java.math.BigDecimal("1.65"));
        c100ItemDiff.setVlCofins(new java.math.BigDecimal("7.60"));
        c100ItemDiff.setCodMod("55");

        SpedFiscalC100 c100TaxDiff = new SpedFiscalC100();
        setId(c100TaxDiff, 201L, SpedFiscalC100.class);
        c100TaxDiff.setFile(file);
        c100TaxDiff.setTenantId(1L);
        c100TaxDiff.setEmpresaId(2L);
        c100TaxDiff.setLinhaOrigem(21);
        c100TaxDiff.setChvNfe("35191111111111111111550010000000011000000021");
        c100TaxDiff.setVlDoc(new java.math.BigDecimal("200.00"));
        c100TaxDiff.setVlIcms(new java.math.BigDecimal("18.00"));
        c100TaxDiff.setVlPis(new java.math.BigDecimal("3.30"));
        c100TaxDiff.setVlCofins(new java.math.BigDecimal("15.20"));
        c100TaxDiff.setCodMod("55");

        when(spedFiscalC100Repository.findByFileIdOrderByLinhaOrigemAsc(eq(31L), any()))
                .thenReturn(new PageImpl<>(List.of(c100ItemDiff, c100TaxDiff), PageRequest.of(0, 50), 2));
        SpedFiscalC170Repository.C100ItemCountProjection count200 = countProjection(200L, 3L);
        SpedFiscalC170Repository.C100ItemCountProjection count201 = countProjection(201L, 2L);
        when(spedFiscalC170Repository.countByC100Ids(any())).thenReturn(List.of(count200, count201));

        SpedFiscalC190Repository.C100IcmsProjection icms200 = icmsProjection(200L, new java.math.BigDecimal("12.00"));
        SpedFiscalC190Repository.C100IcmsProjection icms201 = icmsProjection(201L, new java.math.BigDecimal("18.00"));
        when(spedFiscalC190Repository.sumIcmsByC100Ids(any())).thenReturn(List.of(icms200, icms201));

        FiscalDocument docItemDiff = new FiscalDocument();
        setId(docItemDiff, 601L, FiscalDocument.class);
        docItemDiff.setTenantId(1L);
        docItemDiff.setEmpresaId(2L);
        docItemDiff.setAccessKey(c100ItemDiff.getChvNfe());
        docItemDiff.setTotalAmount(new java.math.BigDecimal("100.00"));
        docItemDiff.setTotalIcms(new java.math.BigDecimal("12.00"));
        docItemDiff.setTotalPis(new java.math.BigDecimal("1.65"));
        docItemDiff.setTotalCofins(new java.math.BigDecimal("7.60"));

        FiscalDocument docTaxDiff = new FiscalDocument();
        setId(docTaxDiff, 602L, FiscalDocument.class);
        docTaxDiff.setTenantId(1L);
        docTaxDiff.setEmpresaId(2L);
        docTaxDiff.setAccessKey(c100TaxDiff.getChvNfe());
        docTaxDiff.setTotalAmount(new java.math.BigDecimal("200.00"));
        docTaxDiff.setTotalIcms(new java.math.BigDecimal("17.00"));
        docTaxDiff.setTotalPis(new java.math.BigDecimal("3.30"));
        docTaxDiff.setTotalCofins(new java.math.BigDecimal("15.20"));

        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKeyIn(eq(1L), eq(2L), any()))
                .thenReturn(List.of(docItemDiff, docTaxDiff));
        FiscalItemRepository.DocumentItemStatsProjection stats601 = documentStatsProjection(
                601L, 2L, new java.math.BigDecimal("12.00"), new java.math.BigDecimal("1.65"), new java.math.BigDecimal("7.60")
        );
        FiscalItemRepository.DocumentItemStatsProjection stats602 = documentStatsProjection(
                602L, 2L, new java.math.BigDecimal("17.00"), new java.math.BigDecimal("3.30"), new java.math.BigDecimal("15.20")
        );
        when(fiscalItemRepository.summarizeByDocumentIds(any())).thenReturn(List.of(stats601, stats602));

        var page = service.reconciliation(31L, 1L, 2L, PageRequest.of(0, 50), auth);

        assertEquals(2, page.getTotalElements());
        assertEquals(SpedReconciliationStatus.ITEM_DIVERGENTE, page.getContent().get(0).status());
        assertEquals("qtd_itens_divergente", page.getContent().get(0).divergenceReason());
        assertEquals(SpedReconciliationStatus.IMPOSTO_DIVERGENTE, page.getContent().get(1).status());
    }

    @Test
    void reconciliation_summary_retorna_agregados() {
        LocalDate inicio = LocalDate.parse("2026-01-01");
        LocalDate fim = LocalDate.parse("2026-01-31");

        when(repository.countByScopeAndPeriodAndStatus(1L, 2L, inicio, fim, SpedFiscalFileStatus.PROCESSADO))
                .thenReturn(2L);

        SpedFiscalFile file = buildSpedFile(40L);
        SpedFiscalC100 c100Ok = new SpedFiscalC100();
        setId(c100Ok, 301L, SpedFiscalC100.class);
        c100Ok.setFile(file);
        c100Ok.setTenantId(1L);
        c100Ok.setEmpresaId(2L);
        c100Ok.setLinhaOrigem(11);
        c100Ok.setChvNfe("35191111111111111111550010000000011000000301");
        c100Ok.setVlDoc(new java.math.BigDecimal("100.00"));
        c100Ok.setVlIcms(new java.math.BigDecimal("12.00"));
        c100Ok.setVlPis(new java.math.BigDecimal("1.65"));
        c100Ok.setVlCofins(new java.math.BigDecimal("7.60"));

        SpedFiscalC100 c100Faltante = new SpedFiscalC100();
        setId(c100Faltante, 302L, SpedFiscalC100.class);
        c100Faltante.setFile(file);
        c100Faltante.setTenantId(1L);
        c100Faltante.setEmpresaId(2L);
        c100Faltante.setLinhaOrigem(12);
        c100Faltante.setChvNfe("35191111111111111111550010000000011000000302");
        c100Faltante.setVlDoc(new java.math.BigDecimal("200.00"));

        when(spedFiscalC100Repository.findByScopeAndPeriod(eq(1L), eq(2L), eq(inicio), eq(fim), eq(SpedFiscalFileStatus.PROCESSADO), any()))
                .thenReturn(new PageImpl<>(List.of(c100Ok, c100Faltante), PageRequest.of(0, 2000), 2));

        SpedFiscalC170Repository.C100ItemCountProjection count301 = countProjection(301L, 2L);
        SpedFiscalC170Repository.C100ItemCountProjection count302 = countProjection(302L, 1L);
        when(spedFiscalC170Repository.countByC100Ids(any())).thenReturn(List.of(count301, count302));

        SpedFiscalC190Repository.C100IcmsProjection icms301 = icmsProjection(301L, new java.math.BigDecimal("12.00"));
        when(spedFiscalC190Repository.sumIcmsByC100Ids(any())).thenReturn(List.of(icms301));

        FiscalDocument xmlDoc = new FiscalDocument();
        setId(xmlDoc, 701L, FiscalDocument.class);
        xmlDoc.setTenantId(1L);
        xmlDoc.setEmpresaId(2L);
        xmlDoc.setAccessKey(c100Ok.getChvNfe());
        xmlDoc.setTotalAmount(new java.math.BigDecimal("100.00"));
        xmlDoc.setTotalIcms(new java.math.BigDecimal("12.00"));
        xmlDoc.setTotalPis(new java.math.BigDecimal("1.65"));
        xmlDoc.setTotalCofins(new java.math.BigDecimal("7.60"));
        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKeyIn(eq(1L), eq(2L), any()))
                .thenReturn(List.of(xmlDoc));

        FiscalItemRepository.DocumentItemStatsProjection xmlStats = documentStatsProjection(
                701L, 2L, new java.math.BigDecimal("12.00"), new java.math.BigDecimal("1.65"), new java.math.BigDecimal("7.60")
        );
        when(fiscalItemRepository.summarizeByDocumentIds(any())).thenReturn(List.of(xmlStats));

        SpedFiscalC170Repository.CodeStatsProjection spedCfop = spedCodeStats("5102", 10L, new java.math.BigDecimal("1500.00"));
        SpedFiscalC170Repository.CodeStatsProjection spedCst = spedCodeStats("060", 7L, new java.math.BigDecimal("700.00"));
        when(spedFiscalC170Repository.summarizeByCfop(eq(1L), eq(2L), eq(inicio), eq(fim), any())).thenReturn(List.of(spedCfop));
        when(spedFiscalC170Repository.summarizeByCstIcms(eq(1L), eq(2L), eq(inicio), eq(fim), any())).thenReturn(List.of(spedCst));

        FiscalItemRepository.CodeStatsProjection xmlCfop = xmlCodeStats("5102", 8L, new java.math.BigDecimal("1200.00"));
        FiscalItemRepository.CodeStatsProjection xmlNcm = xmlCodeStats("84713012", 5L, new java.math.BigDecimal("900.00"));
        when(fiscalItemRepository.summarizeByCfop(eq(1L), eq(2L), eq(inicio), eq(fim), any())).thenReturn(List.of(xmlCfop));
        when(fiscalItemRepository.summarizeByNcm(eq(1L), eq(2L), eq(inicio), eq(fim), any())).thenReturn(List.of(xmlNcm));

        SpedFiscalE110Repository.E110AggregateProjection e110 = e110Projection(1L, new java.math.BigDecimal("1000.00"), new java.math.BigDecimal("850.00"), new java.math.BigDecimal("150.00"));
        when(spedFiscalE110Repository.summarizeByScopeAndPeriod(1L, 2L, inicio, fim, SpedFiscalFileStatus.PROCESSADO)).thenReturn(e110);
        when(spedFiscalE111Repository.sumAdjustmentsByScopeAndPeriod(1L, 2L, inicio, fim, SpedFiscalFileStatus.PROCESSADO))
                .thenReturn(new java.math.BigDecimal("25.00"));
        SpedFiscalE111Repository.E111CodeProjection adj = e111CodeProjection("SP000001", 2L, new java.math.BigDecimal("25.00"));
        when(spedFiscalE111Repository.summarizeTopAdjustmentsByScopeAndPeriod(eq(1L), eq(2L), eq(inicio), eq(fim), eq(SpedFiscalFileStatus.PROCESSADO), any()))
                .thenReturn(List.of(adj));

        var summary = service.reconciliationSummary(1L, 2L, inicio, fim, 20, auth);

        assertEquals(2L, summary.totalArquivosProcessados());
        assertEquals(2L, summary.totalDocumentosSped());
        assertEquals(1L, summary.totalOk());
        assertEquals(1L, summary.totalFaltanteXml());
        assertEquals(1, summary.cfopSpedTop().size());
        assertEquals(1, summary.cfopXmlTop().size());
        assertEquals(1L, summary.apuracao().totalRegistrosE110());
        assertEquals(new java.math.BigDecimal("25.00"), summary.apuracao().totalAjustesE111());
    }

    @Test
    void reconciliation_divergences_por_periodo_filtra_apenas_divergente() {
        LocalDate inicio = LocalDate.parse("2026-02-01");
        LocalDate fim = LocalDate.parse("2026-02-28");

        SpedFiscalFile file = buildSpedFile(50L);
        SpedFiscalC100 c100Ok = new SpedFiscalC100();
        setId(c100Ok, 401L, SpedFiscalC100.class);
        c100Ok.setFile(file);
        c100Ok.setTenantId(1L);
        c100Ok.setEmpresaId(2L);
        c100Ok.setLinhaOrigem(10);
        c100Ok.setChvNfe("35191111111111111111550010000000011000000401");
        c100Ok.setVlDoc(new java.math.BigDecimal("100.00"));
        c100Ok.setCodMod("55");

        SpedFiscalC100 c100Missing = new SpedFiscalC100();
        setId(c100Missing, 402L, SpedFiscalC100.class);
        c100Missing.setFile(file);
        c100Missing.setTenantId(1L);
        c100Missing.setEmpresaId(2L);
        c100Missing.setLinhaOrigem(11);
        c100Missing.setChvNfe("35191111111111111111550010000000011000000402");
        c100Missing.setVlDoc(new java.math.BigDecimal("200.00"));
        c100Missing.setCodMod("55");

        when(spedFiscalC100Repository.findByScopeAndPeriod(eq(1L), eq(2L), eq(inicio), eq(fim), eq(SpedFiscalFileStatus.PROCESSADO), any()))
                .thenReturn(new PageImpl<>(List.of(c100Ok, c100Missing), PageRequest.of(0, 2000), 2));

        FiscalDocument xmlDoc = new FiscalDocument();
        setId(xmlDoc, 801L, FiscalDocument.class);
        xmlDoc.setTenantId(1L);
        xmlDoc.setEmpresaId(2L);
        xmlDoc.setAccessKey(c100Ok.getChvNfe());
        xmlDoc.setTotalAmount(new java.math.BigDecimal("100.00"));

        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKeyIn(eq(1L), eq(2L), any()))
                .thenReturn(List.of(xmlDoc));
        when(spedFiscalC170Repository.countByC100Ids(any())).thenReturn(List.of());
        when(spedFiscalC190Repository.sumIcmsByC100Ids(any())).thenReturn(List.of());
        when(fiscalItemRepository.summarizeByDocumentIds(any())).thenReturn(List.of());

        var page = service.reconciliationDivergences(
                1L,
                2L,
                inicio,
                fim,
                null,
                null,
                PageRequest.of(0, 20),
                auth
        );

        assertEquals(1, page.getTotalElements());
        assertEquals(SpedReconciliationStatus.FALTANTE_XML, page.getContent().get(0).status());
        assertEquals(c100Missing.getChvNfe(), page.getContent().get(0).accessKey());
    }

    private SpedFiscalFile buildSpedFile(Long id) {
        SpedFiscalFile file = new SpedFiscalFile();
        setId(file, id, SpedFiscalFile.class);
        file.setTenantId(1L);
        file.setEmpresaId(2L);
        file.setStatus(SpedFiscalFileStatus.PROCESSADO);
        file.setSourceType(SpedFiscalFileSourceType.TXT);
        file.setOriginalFileName("arquivo.txt");
        file.setStorageObjectKey("sped/1/2/a.txt");
        file.setHashSha256("hash");
        return file;
    }

    private void setId(SpedFiscalFile file, Long id) {
        setId(file, id, SpedFiscalFile.class);
    }

    private void setId(Object file, Long id, Class<?> type) {
        try {
            var field = type.getDeclaredField("id");
            field.setAccessible(true);
            field.set(file, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private SpedFiscalC170Repository.C100ItemCountProjection countProjection(Long c100Id, Long totalItens) {
        SpedFiscalC170Repository.C100ItemCountProjection projection = mock(SpedFiscalC170Repository.C100ItemCountProjection.class);
        when(projection.getC100Id()).thenReturn(c100Id);
        when(projection.getTotalItens()).thenReturn(totalItens);
        return projection;
    }

    private SpedFiscalC190Repository.C100IcmsProjection icmsProjection(Long c100Id, java.math.BigDecimal totalIcms) {
        SpedFiscalC190Repository.C100IcmsProjection projection = mock(SpedFiscalC190Repository.C100IcmsProjection.class);
        when(projection.getC100Id()).thenReturn(c100Id);
        when(projection.getTotalIcms()).thenReturn(totalIcms);
        return projection;
    }

    private FiscalItemRepository.DocumentItemStatsProjection documentStatsProjection(Long documentId,
                                                                                     Long totalItens,
                                                                                     java.math.BigDecimal totalIcms,
                                                                                     java.math.BigDecimal totalPis,
                                                                                     java.math.BigDecimal totalCofins) {
        FiscalItemRepository.DocumentItemStatsProjection projection = mock(FiscalItemRepository.DocumentItemStatsProjection.class);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getTotalItens()).thenReturn(totalItens);
        when(projection.getTotalIcms()).thenReturn(totalIcms);
        when(projection.getTotalPis()).thenReturn(totalPis);
        when(projection.getTotalCofins()).thenReturn(totalCofins);
        return projection;
    }

    private SpedFiscalC170Repository.CodeStatsProjection spedCodeStats(String codigo, Long totalItens, java.math.BigDecimal totalValor) {
        SpedFiscalC170Repository.CodeStatsProjection projection = mock(SpedFiscalC170Repository.CodeStatsProjection.class);
        when(projection.getCodigo()).thenReturn(codigo);
        when(projection.getTotalItens()).thenReturn(totalItens);
        when(projection.getTotalValor()).thenReturn(totalValor);
        return projection;
    }

    private FiscalItemRepository.CodeStatsProjection xmlCodeStats(String codigo, Long totalItens, java.math.BigDecimal totalValor) {
        FiscalItemRepository.CodeStatsProjection projection = mock(FiscalItemRepository.CodeStatsProjection.class);
        when(projection.getCodigo()).thenReturn(codigo);
        when(projection.getTotalItens()).thenReturn(totalItens);
        when(projection.getTotalValor()).thenReturn(totalValor);
        return projection;
    }

    private SpedFiscalE110Repository.E110AggregateProjection e110Projection(Long totalRegistros,
                                                                             java.math.BigDecimal totalDebitos,
                                                                             java.math.BigDecimal totalCreditos,
                                                                             java.math.BigDecimal totalIcmsRecolher) {
        SpedFiscalE110Repository.E110AggregateProjection projection = mock(SpedFiscalE110Repository.E110AggregateProjection.class);
        when(projection.getTotalRegistros()).thenReturn(totalRegistros);
        when(projection.getTotalDebitos()).thenReturn(totalDebitos);
        when(projection.getTotalCreditos()).thenReturn(totalCreditos);
        when(projection.getTotalIcmsRecolher()).thenReturn(totalIcmsRecolher);
        return projection;
    }

    private SpedFiscalE111Repository.E111CodeProjection e111CodeProjection(String codigo,
                                                                            Long totalRegistros,
                                                                            java.math.BigDecimal totalValor) {
        SpedFiscalE111Repository.E111CodeProjection projection = mock(SpedFiscalE111Repository.E111CodeProjection.class);
        when(projection.getCodigo()).thenReturn(codigo);
        when(projection.getTotalRegistros()).thenReturn(totalRegistros);
        when(projection.getTotalValor()).thenReturn(totalValor);
        return projection;
    }
}
