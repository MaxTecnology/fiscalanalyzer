package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlRequest;
import br.com.techbr.fiscalanalyzer.agent.dto.AgentUploadUrlResponse;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.agent.service.AgentUploadAuditService;
import br.com.techbr.fiscalanalyzer.agent.service.AgentUploadUrlService;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentRegistry;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentAdditionalInfoRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentDuplicateRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentEventRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentPaymentRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRegistryRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestEntryRequest;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestRequest;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import br.com.techbr.fiscalanalyzer.queue.producer.ExtractZipProducer;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentManifestParseFlowTest {

    @Test
    void fluxoUploadUrlManifestParse_concluiComSucesso() {
        StorageService storageService = mock(StorageService.class);
        AgentUploadAuditService auditService = mock(AgentUploadAuditService.class);
        ImportacaoRepository importacaoRepository = mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = mock(ImportItemRepository.class);
        FiscalDocumentRepository fiscalDocumentRepository = mock(FiscalDocumentRepository.class);
        FiscalDocumentEventRepository fiscalDocumentEventRepository = mock(FiscalDocumentEventRepository.class);
        FiscalDocumentRegistryRepository registryRepository = mock(FiscalDocumentRegistryRepository.class);
        FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository = mock(FiscalDocumentPaymentRepository.class);
        FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository = mock(FiscalDocumentDuplicateRepository.class);
        FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository = mock(FiscalDocumentAdditionalInfoRepository.class);
        FiscalItemRepository fiscalItemRepository = mock(FiscalItemRepository.class);
        ExtractZipProducer extractZipProducer = mock(ExtractZipProducer.class);
        TenantEmpresaValidationService tenantEmpresaValidationService = mock(TenantEmpresaValidationService.class);

        AgentUploadUrlService uploadUrlService = new AgentUploadUrlService(storageService, auditService, 900, 20L * 1024 * 1024);
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher eventPublisher = publishedEvents::add;
        ImportacaoService importacaoService = new ImportacaoService(
                importacaoRepository,
                importItemRepository,
                storageService,
                extractZipProducer,
                eventPublisher,
                tenantEmpresaValidationService,
                DataSize.ofMegabytes(100),
                "fiscal-raw"
        );
        ParseXmlService parseXmlService = new ParseXmlService(
                importacaoRepository,
                importItemRepository,
                fiscalDocumentRepository,
                fiscalDocumentEventRepository,
                registryRepository,
                fiscalDocumentPaymentRepository,
                fiscalDocumentDuplicateRepository,
                fiscalDocumentAdditionalInfoRepository,
                fiscalItemRepository,
                storageService,
                new SimpleMeterRegistry()
        );

        Map<Long, Importacao> importacoes = new HashMap<>();
        Map<Long, ImportItem> items = new HashMap<>();
        Map<String, FiscalDocumentRegistry> registries = new HashMap<>();
        List<FiscalDocument> savedDocuments = new ArrayList<>();
        Set<String> storageObjects = new HashSet<>();
        AtomicLong importacaoIdSequence = new AtomicLong(1);
        AtomicLong importItemIdSequence = new AtomicLong(100);
        AtomicLong fiscalDocumentIdSequence = new AtomicLong(1000);

        when(importacaoRepository.save(any(Importacao.class))).thenAnswer(invocation -> {
            Importacao importacao = invocation.getArgument(0);
            if (importacao.getId() == null) {
                ReflectionTestUtils.setField(importacao, "id", importacaoIdSequence.getAndIncrement());
            }
            importacoes.put(importacao.getId(), importacao);
            return importacao;
        });
        when(importacaoRepository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(importacoes.get(invocation.getArgument(0, Long.class)))
        );
        when(importacaoRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(importacoes.get(invocation.getArgument(0, Long.class)))
        );

        when(importItemRepository.findByImportacaoIdAndXmlPath(anyLong(), anyString())).thenAnswer(invocation -> {
            Long importacaoId = invocation.getArgument(0, Long.class);
            String xmlPath = invocation.getArgument(1, String.class);
            return items.values().stream()
                    .filter(item -> item.getImportacao() != null
                            && importacaoId.equals(item.getImportacao().getId())
                            && xmlPath.equals(item.getXmlPath()))
                    .findFirst();
        });
        when(importItemRepository.save(any(ImportItem.class))).thenAnswer(invocation -> {
            ImportItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                ReflectionTestUtils.setField(item, "id", importItemIdSequence.getAndIncrement());
            }
            items.put(item.getId(), item);
            return item;
        });
        when(importItemRepository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(items.get(invocation.getArgument(0, Long.class)))
        );
        when(importItemRepository.countByImportacaoIdAndStatusIn(anyLong(), anyCollection())).thenAnswer(invocation -> {
            Long importacaoId = invocation.getArgument(0, Long.class);
            Collection<ImportItemStatus> statuses = invocation.getArgument(1);
            return items.values().stream()
                    .filter(item -> item.getImportacao() != null && importacaoId.equals(item.getImportacao().getId()))
                    .filter(item -> statuses.contains(item.getStatus()))
                    .count();
        });
        when(importItemRepository.countByImportacaoId(anyLong())).thenAnswer(invocation -> {
            Long importacaoId = invocation.getArgument(0, Long.class);
            return items.values().stream()
                    .filter(item -> item.getImportacao() != null && importacaoId.equals(item.getImportacao().getId()))
                    .count();
        });

        when(fiscalDocumentRepository.save(any(FiscalDocument.class))).thenAnswer(invocation -> {
            FiscalDocument document = invocation.getArgument(0);
            if (document.getId() == null) {
                ReflectionTestUtils.setField(document, "id", fiscalDocumentIdSequence.getAndIncrement());
            }
            savedDocuments.add(document);
            return document;
        });

        when(registryRepository.findByTenantIdAndEmpresaIdAndAccessKey(anyLong(), anyLong(), anyString())).thenAnswer(invocation -> {
            Long tenantId = invocation.getArgument(0, Long.class);
            Long empresaId = invocation.getArgument(1, Long.class);
            String accessKey = invocation.getArgument(2, String.class);
            return Optional.ofNullable(registries.get(registryKey(tenantId, empresaId, accessKey)));
        });
        when(registryRepository.save(any(FiscalDocumentRegistry.class))).thenAnswer(invocation -> {
            FiscalDocumentRegistry registry = invocation.getArgument(0);
            registries.put(registryKey(registry.getTenantId(), registry.getEmpresaId(), registry.getAccessKey()), registry);
            return registry;
        });

        when(fiscalItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(storageService.exists(anyString())).thenAnswer(invocation -> storageObjects.contains(invocation.getArgument(0, String.class)));

        String sha256 = "9f81409800c24998f96a4f8e2e1bab1dfe93144563fcb2c3b946b5461ad15869";
        String xmlBody = minimalNfeXml();
        byte[] xmlBytes = xmlBody.getBytes(StandardCharsets.UTF_8);

        AgentAuthContext auth = new AgentAuthContext(1L, 99L, 42L, "fa_live_ab");
        AgentUploadUrlRequest uploadRequest = new AgentUploadUrlRequest(sha256, "nfe.xml", (long) xmlBytes.length);

        when(storageService.generatePresignedPutUrl(anyString(), anyInt(), anyString()))
                .thenReturn("https://upload.example/presigned");

        AgentUploadUrlResponse uploadResponse = uploadUrlService.generateUploadUrl(auth, uploadRequest);
        storageObjects.add(uploadResponse.objectKey());
        when(storageService.get(uploadResponse.objectKey())).thenReturn(new ByteArrayInputStream(xmlBytes));

        ManifestRequest manifest = new ManifestRequest(
                99L,
                42L,
                List.of(new ManifestEntryRequest(uploadResponse.objectKey(), sha256, (long) xmlBytes.length, "nfe.xml"))
        );

        Importacao importacao = importacaoService.criarImportacaoPorManifesto(manifest, 99L, 42L);

        assertEquals(1, publishedEvents.size());
        Object eventObject = publishedEvents.getFirst();
        ParseXmlRequestedEvent event = assertInstanceOf(ParseXmlRequestedEvent.class, eventObject);

        parseXmlService.process(new ParseXmlMessage(
                event.importacaoId(),
                event.importItemId(),
                event.bucket(),
                event.objectKeyZip(),
                event.zipEntryName(),
                event.sha256()
        ), event.correlationId());

        ImportItem parsedItem = items.get(event.importItemId());
        assertEquals(ImportItemStatus.PARSEADO, parsedItem.getStatus());
        assertEquals("35191111111111111111550010000000011000000010", parsedItem.getAccessKey());
        assertEquals(LocalDate.of(2024, 1, 2), parsedItem.getIssueDate());

        assertEquals(1, savedDocuments.size());
        FiscalDocument document = savedDocuments.getFirst();
        assertEquals(uploadResponse.objectKey(), document.getXmlPath());
        assertEquals((short) 55, document.getModel());
        assertEquals("S", document.getOperationType());
        assertEquals("11111111111111", document.getEmitCnpj());
        assertEquals("22222222222222", document.getDestCnpj());

        assertEquals(ImportacaoStatus.CONCLUIDO, importacoes.get(importacao.getId()).getStatus());
        assertEquals(1, registries.size());
    }

    private String registryKey(Long tenantId, Long empresaId, String accessKey) {
        return tenantId + "|" + empresaId + "|" + accessKey;
    }

    private String minimalNfeXml() {
        return """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <infNFe Id="NFe35191111111111111111550010000000011000000010">
                    <ide>
                      <mod>55</mod>
                      <tpNF>1</tpNF>
                      <dEmi>2024-01-02</dEmi>
                    </ide>
                    <emit><CNPJ>11111111111111</CNPJ></emit>
                    <dest><CNPJ>22222222222222</CNPJ></dest>
                    <total><ICMSTot><vNF>123.45</vNF></ICMSTot></total>
                  </infNFe>
                </NFe>
                """;
    }
}
