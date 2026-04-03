package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.security.UserAuthContext;
import br.com.techbr.fiscalanalyzer.identity.service.UserAuthorizationService;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportReprocessRequest;
import br.com.techbr.fiscalanalyzer.importacao.dto.ImportReprocessResponse;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportacaoReprocessServiceTest {

    @Test
    void reprocess_manifest_reenfileiraItem() {
        ImportacaoRepository importacaoRepository = mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = mock(ImportItemRepository.class);
        UserAuthorizationService authorizationService = mock(UserAuthorizationService.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);

        ImportacaoReprocessService service = new ImportacaoReprocessService(
                importacaoRepository,
                importItemRepository,
                authorizationService,
                eventPublisher,
                "fiscal-analyzer"
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 10L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);
        importacao.setSourceType(ImportacaoSourceType.MANIFEST);
        importacao.setStatus(ImportacaoStatus.CONCLUIDO);

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 99L);
        item.setImportacao(importacao);
        item.setStatus(ImportItemStatus.FALHA_PARSE);
        item.setStorageObjectKey("1/2/abc.xml");
        item.setXmlPath("1/2/abc.xml");
        item.setXmlHash("a".repeat(64));
        item.setErroCodigo("FALHA_PARSE");
        item.setErroMensagem("xml_tipo_nao_suportado");

        when(importacaoRepository.findById(10L)).thenReturn(Optional.of(importacao));
        when(importItemRepository.findByImportacaoIdAndStatusIn(eq(10L), anyCollection(), any()))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(importItemRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(importItemRepository.countByImportacaoId(10L)).thenReturn(1L);
        when(importItemRepository.countByImportacaoIdAndStatusIn(eq(10L), anyCollection())).thenReturn(0L, 0L);
        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(i -> i.getArgument(0));

        ImportReprocessResponse response = service.reprocess(
                10L,
                new ImportReprocessRequest(List.of(ImportItemStatus.FALHA_PARSE), null, 100, false),
                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))
        );

        assertEquals(1, response.selectedItems());
        assertEquals(1, response.requeuedItems());
        assertNotNull(response.correlationId());
        assertEquals(ImportItemStatus.PENDENTE_PARSE, item.getStatus());
        assertEquals(null, item.getErroCodigo());
        assertEquals(null, item.getErroMensagem());
        assertEquals(ImportacaoStatus.PROCESSANDO, importacao.getStatus());

        ArgumentCaptor<ParseXmlRequestedEvent> eventCaptor = ArgumentCaptor.forClass(ParseXmlRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ParseXmlRequestedEvent event = eventCaptor.getValue();
        assertEquals(10L, event.importacaoId());
        assertEquals(99L, event.importItemId());
        assertEquals("1/2/abc.xml", event.objectKeyZip());
        assertEquals(null, event.zipEntryName());
    }

    @Test
    void reprocess_dryRun_naoPublicaEventos() {
        ImportacaoRepository importacaoRepository = mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = mock(ImportItemRepository.class);
        UserAuthorizationService authorizationService = mock(UserAuthorizationService.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);

        ImportacaoReprocessService service = new ImportacaoReprocessService(
                importacaoRepository,
                importItemRepository,
                authorizationService,
                eventPublisher,
                "fiscal-analyzer"
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 20L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);
        importacao.setSourceType(ImportacaoSourceType.ZIP);
        importacao.setArquivoPath("imports/20/a.zip");

        ImportItem item = new ImportItem();
        ReflectionTestUtils.setField(item, "id", 201L);
        item.setStatus(ImportItemStatus.FALHA_PERMANENTE);
        item.setXmlPath("abc.xml");

        when(importacaoRepository.findById(20L)).thenReturn(Optional.of(importacao));
        when(importItemRepository.findByImportacaoIdAndStatusIn(eq(20L), anyCollection(), any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        ImportReprocessResponse response = service.reprocess(
                20L,
                new ImportReprocessRequest(List.of(ImportItemStatus.FALHA_PERMANENTE), null, 10, true),
                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))
        );

        assertEquals(1, response.selectedItems());
        assertEquals(0, response.requeuedItems());
        assertEquals(true, response.dryRun());
        verify(importItemRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(importacaoRepository, never()).save(any());
    }

    @Test
    void reprocess_statusNaoPermitido_retorna400() {
        ImportacaoRepository importacaoRepository = mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = mock(ImportItemRepository.class);
        UserAuthorizationService authorizationService = mock(UserAuthorizationService.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);

        ImportacaoReprocessService service = new ImportacaoReprocessService(
                importacaoRepository,
                importItemRepository,
                authorizationService,
                eventPublisher,
                "fiscal-analyzer"
        );

        Importacao importacao = new Importacao();
        ReflectionTestUtils.setField(importacao, "id", 30L);
        importacao.setTenantId(1L);
        importacao.setEmpresaId(2L);

        when(importacaoRepository.findById(anyLong())).thenReturn(Optional.of(importacao));

        assertThrows(ValidationException.class, () -> service.reprocess(
                30L,
                new ImportReprocessRequest(List.of(ImportItemStatus.PARSEADO), null, 100, false),
                new UserAuthContext(7L, "operador@empresa.com", List.of("OPERADOR"))
        ));
    }
}
