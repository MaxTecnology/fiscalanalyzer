package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoDetailResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportFailureSummaryResponse;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoSummaryResponse;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportacaoReadServiceTest {

    @Test
    void getDetail_retornaContadores() {
        ImportacaoRepository importacaoRepository = Mockito.mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = Mockito.mock(ImportItemRepository.class);
        UserAuthorizationService userAuthorizationService = Mockito.mock(UserAuthorizationService.class);
        ImportacaoReadService service = new ImportacaoReadService(importacaoRepository, importItemRepository, userAuthorizationService);

        Importacao imp = new Importacao();
        ReflectionTestUtils.setField(imp, "id", 1L);
        imp.setStatus(ImportacaoStatus.EXTRAIDO);
        imp.setArquivoNome("a.zip");
        imp.setArquivoHash("hash");
        ReflectionTestUtils.setField(imp, "createdAt", Instant.now());
        ReflectionTestUtils.setField(imp, "updatedAt", Instant.now());

        when(importacaoRepository.findById(1L)).thenReturn(Optional.of(imp));
        when(importItemRepository.countByImportacaoId(1L)).thenReturn(5L);
        when(importItemRepository.countByStatus(1L)).thenReturn(List.of(
                new StatusCount(ImportItemStatus.PARSEADO, 3),
                new StatusCount(ImportItemStatus.FALHA_PARSE, 2)
        ));

        ImportacaoDetailResponse response = service.getDetail(1L, new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR")));
        assertEquals(5L, response.totalItems());
        assertEquals(2, response.statusCounts().size());
    }

    @Test
    void listImports_filtraPorTenantEmpresaStatus() {
        ImportacaoRepository importacaoRepository = Mockito.mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = Mockito.mock(ImportItemRepository.class);
        UserAuthorizationService userAuthorizationService = Mockito.mock(UserAuthorizationService.class);
        ImportacaoReadService service = new ImportacaoReadService(importacaoRepository, importItemRepository, userAuthorizationService);

        Importacao imp = new Importacao();
        ReflectionTestUtils.setField(imp, "id", 11L);
        imp.setStatus(ImportacaoStatus.CONCLUIDO);
        imp.setSourceType(ImportacaoSourceType.ZIP);
        imp.setTotalEncontrado(50);
        imp.setTotalProcessado(48);
        imp.setTotalErros(2);
        ReflectionTestUtils.setField(imp, "createdAt", Instant.now());
        ReflectionTestUtils.setField(imp, "updatedAt", Instant.now());

        PageRequest pageable = PageRequest.of(0, 20);
        UserAuthContext auth = new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"));
        when(importacaoRepository.findByTenantIdAndEmpresaIdAndStatus(99L, 42L, ImportacaoStatus.CONCLUIDO, pageable))
                .thenReturn(new PageImpl<>(List.of(imp), pageable, 1));

        Page<ImportacaoSummaryResponse> page = service.listImports(
                99L,
                42L,
                ImportacaoStatus.CONCLUIDO,
                pageable,
                auth
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("CONCLUIDO", page.getContent().getFirst().status());
        assertEquals(50L, page.getContent().getFirst().totalItems());
        verify(userAuthorizationService).assertCanRead(eq(auth), eq(99L), eq(42L));
    }

    private record StatusCount(ImportItemStatus status, long count)
            implements ImportItemRepository.StatusCountProjection {
        @Override public ImportItemStatus getStatus() { return status; }
        @Override public long getCount() { return count; }
    }

    @Test
    void summarizeFailures_retornaAgrupado() {
        ImportacaoRepository importacaoRepository = Mockito.mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = Mockito.mock(ImportItemRepository.class);
        UserAuthorizationService userAuthorizationService = Mockito.mock(UserAuthorizationService.class);
        ImportacaoReadService service = new ImportacaoReadService(importacaoRepository, importItemRepository, userAuthorizationService);

        Importacao imp = new Importacao();
        ReflectionTestUtils.setField(imp, "id", 1L);
        imp.setTenantId(99L);
        imp.setEmpresaId(42L);
        when(importacaoRepository.findById(1L)).thenReturn(Optional.of(imp));
        when(importItemRepository.summarizeFailures(1L)).thenReturn(List.of(
                new FailureSummary("FALHA_PARSE", "xml_tipo_nao_suportado:root=retEnviNFe,ns=http://www.portalfiscal.inf.br/nfe",
                        9L, 77L, "1/2/a.xml", Instant.parse("2026-04-02T21:06:49Z"))
        ));

        List<ImportFailureSummaryResponse> summary = service.summarizeFailures(
                1L,
                new UserAuthContext(7L, "leitor@empresa.com", List.of("LEITOR"))
        );

        assertEquals(1, summary.size());
        assertEquals("FALHA_PARSE", summary.getFirst().erroCodigo());
        assertEquals(9L, summary.getFirst().totalItems());
        assertEquals("1/2/a.xml", summary.getFirst().sampleXmlPath());
    }

    private record FailureSummary(String erroCodigo,
                                  String erroMensagem,
                                  long totalItems,
                                  Long sampleItemId,
                                  String sampleXmlPath,
                                  Instant lastOccurrenceAt)
            implements ImportItemRepository.FailureSummaryProjection {
        @Override public String getErroCodigo() { return erroCodigo; }
        @Override public String getErroMensagem() { return erroMensagem; }
        @Override public long getTotalItems() { return totalItems; }
        @Override public Long getSampleItemId() { return sampleItemId; }
        @Override public String getSampleXmlPath() { return sampleXmlPath; }
        @Override public Instant getLastOccurrenceAt() { return lastOccurrenceAt; }
    }
}
