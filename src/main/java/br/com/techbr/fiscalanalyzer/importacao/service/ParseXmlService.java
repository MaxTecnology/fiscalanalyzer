package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentAdditionalInfo;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentDuplicate;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentEvent;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentPayment;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentRegistry;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentAdditionalInfoRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentDuplicateRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentEventRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentPaymentRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRegistryRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.item.model.FiscalItem;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import br.com.techbr.fiscalanalyzer.xml.parser.NfeEventXmlParser;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeAdditionalInfo;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeDuplicate;
import br.com.techbr.fiscalanalyzer.xml.parser.NfeXmlParser;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeEvent;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfe;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeItem;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfePayment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ParseXmlService {

    private static final Logger log = LoggerFactory.getLogger(ParseXmlService.class);
    private static final Set<ImportItemStatus> FINAL_STATUSES = EnumSet.of(
            ImportItemStatus.PARSEADO,
            ImportItemStatus.DUPLICADO,
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.CONCLUIDO,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );
    private static final Set<ImportItemStatus> PROCESSED_STATUSES = EnumSet.of(
            ImportItemStatus.PARSEADO,
            ImportItemStatus.DUPLICADO,
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.CONCLUIDO,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );
    private static final Set<ImportItemStatus> ERROR_STATUSES = EnumSet.of(
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );

    private final ImportacaoRepository importacaoRepository;
    private final ImportItemRepository importItemRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentEventRepository fiscalDocumentEventRepository;
    private final FiscalDocumentRegistryRepository registryRepository;
    private final FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository;
    private final FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository;
    private final FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository;
    private final FiscalItemRepository fiscalItemRepository;
    private final StorageService storageService;
    private final NfeXmlParser nfeXmlParser;
    private final NfeEventXmlParser nfeEventXmlParser;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter duplicateCounter;
    private final Timer parseTimer;
    private final MeterRegistry meterRegistry;

    public ParseXmlService(ImportacaoRepository importacaoRepository,
                           ImportItemRepository importItemRepository,
                           FiscalDocumentRepository fiscalDocumentRepository,
                           FiscalDocumentEventRepository fiscalDocumentEventRepository,
                           FiscalDocumentRegistryRepository registryRepository,
                           FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository,
                           FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository,
                           FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository,
                           FiscalItemRepository fiscalItemRepository,
                           StorageService storageService,
                           MeterRegistry meterRegistry) {
        this.importacaoRepository = importacaoRepository;
        this.importItemRepository = importItemRepository;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.fiscalDocumentEventRepository = fiscalDocumentEventRepository;
        this.registryRepository = registryRepository;
        this.fiscalDocumentPaymentRepository = fiscalDocumentPaymentRepository;
        this.fiscalDocumentDuplicateRepository = fiscalDocumentDuplicateRepository;
        this.fiscalDocumentAdditionalInfoRepository = fiscalDocumentAdditionalInfoRepository;
        this.fiscalItemRepository = fiscalItemRepository;
        this.storageService = storageService;
        this.nfeXmlParser = new NfeXmlParser();
        this.nfeEventXmlParser = new NfeEventXmlParser();
        this.meterRegistry = meterRegistry;
        this.successCounter = meterRegistry.counter("import.parse.success");
        this.failureCounter = meterRegistry.counter("import.parse.failure");
        this.duplicateCounter = meterRegistry.counter("import.parse.duplicate");
        this.parseTimer = Timer.builder("import.parse.duration").register(meterRegistry);
    }

    @Transactional
    public void process(ParseXmlMessage message, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        ImportItem item = importItemRepository.findById(message.importItemId())
                .orElseThrow(() -> new ValidationException("ImportItem nao encontrado: " + message.importItemId()));

        if (FINAL_STATUSES.contains(item.getStatus())) {
            return;
        }

        Importacao importacao = importacaoRepository.findById(message.importacaoId())
                .orElseThrow(() -> new ValidationException("Importacao nao encontrada: " + message.importacaoId()));

        importacao.setStatus(ImportacaoStatus.PROCESSANDO);
        importacaoRepository.save(importacao);

        item.setStatus(ImportItemStatus.PROCESSANDO);
        importItemRepository.save(item);

        try {
            ParsedPayload payload = parsePayload(message, item);
            if (payload.xmlType() == ParsedXmlType.EVENT) {
                processEvent(importacao, item, payload, correlationId);
            } else {
                processDocument(importacao, item, payload, correlationId);
            }
        } catch (ValidationException e) {
            failItem(item, "FALHA_PARSE", e.getMessage());
            finalizeImportacao(importacao, item, true);
            failureCounter.increment();
        } catch (InfraException e) {
            throw e;
        } catch (Exception e) {
            throw new InfraException("Falha ao ler ZIP/XML", e);
        } finally {
            sample.stop(parseTimer);
        }
    }

    private void failItem(ImportItem item, String code, String message) {
        item.setStatus(ImportItemStatus.FALHA_PARSE);
        item.setErroCodigo(code);
        item.setErroMensagem(message);
        importItemRepository.save(item);
    }

    private void processDocument(Importacao importacao, ImportItem item, ParsedPayload payload, String correlationId) {
        ParsedNfe parsed = payload.parsedNfe();
        applyItemMetadata(item, payload.xmlHash(), payload.xmlPath(), payload.xmlSize());
        item.setModel(parsed.model());
        item.setAccessKey(parsed.accessKey());
        item.setIssueDate(parsed.issueDate());

        Optional<FiscalDocumentRegistry> existing = registryRepository
                .findByTenantIdAndEmpresaIdAndAccessKey(importacao.getTenantId(), importacao.getEmpresaId(), parsed.accessKey());

        if (existing.isPresent()) {
            markItemDuplicado(importacao, item, correlationId, parsed.accessKey(), false);
            return;
        }

        FiscalDocument doc = new FiscalDocument();
        doc.setTenantId(importacao.getTenantId());
        doc.setEmpresaId(importacao.getEmpresaId());
        doc.setModel(parsed.model());
        doc.setAccessKey(parsed.accessKey());
        doc.setNumeroNota(parsed.numeroNota());
        doc.setSerie(parsed.serie());
        doc.setNaturezaOperacao(parsed.naturezaOperacao());
        doc.setIssueDate(parsed.issueDate());
        doc.setIssueDateTime(parsed.issueDateTime());
        doc.setAmbiente(parsed.ambiente());
        doc.setFinalidadeEmissao(parsed.finalidadeEmissao());
        doc.setConsumidorFinal(parsed.consumidorFinal());
        doc.setPresencaComprador(parsed.presencaComprador());
        doc.setOperationType(parsed.operationType());
        doc.setEmitCnpj(parsed.emitCnpj());
        doc.setEmitNome(parsed.emitName());
        doc.setEmitIe(parsed.emitIe());
        doc.setEmitUf(parsed.emitUf());
        doc.setDestDocumento(parsed.destDocument());
        doc.setDestCnpj(parsed.destCnpj());
        doc.setDestNome(parsed.destName());
        doc.setDestIe(parsed.destIe());
        doc.setDestUf(parsed.destUf());
        doc.setTotalProducts(parsed.totalProducts());
        doc.setTotalAmount(parsed.totalAmount());
        doc.setTotalFrete(parsed.totalFrete());
        doc.setTotalDesconto(parsed.totalDesconto());
        doc.setTotalOutros(parsed.totalOutros());
        doc.setTotalIpi(parsed.totalIpi());
        doc.setTotalTributos(parsed.totalTributos());
        doc.setTotalIcms(parsed.totalIcms());
        doc.setTotalPis(parsed.totalPis());
        doc.setTotalCofins(parsed.totalCofins());
        doc.setProtocoloNumero(parsed.protocoloNumero());
        doc.setProtocoloStatus(parsed.protocoloStatus());
        doc.setProtocoloMotivo(parsed.protocoloMotivo());
        doc.setProtocoloRecebimento(parsed.protocoloRecebimento());
        doc.setQrCodeUrl(parsed.qrCodeUrl());
        doc.setFaturaNumero(parsed.faturaNumero());
        doc.setFaturaValorOriginal(parsed.faturaValorOriginal());
        doc.setFaturaValorDesconto(parsed.faturaValorDesconto());
        doc.setFaturaValorLiquido(parsed.faturaValorLiquido());
        doc.setImportacao(importacao);
        doc.setXmlPath(resolveXmlPath(item, payload.xmlPath()));
        doc.setXmlHash(payload.xmlHash());

        FiscalDocument savedDoc = fiscalDocumentRepository.save(doc);

        try {
            FiscalDocumentRegistry registry = new FiscalDocumentRegistry();
            registry.setTenantId(importacao.getTenantId());
            registry.setEmpresaId(importacao.getEmpresaId());
            registry.setAccessKey(parsed.accessKey());
            registry.setFiscalDocument(savedDoc);
            registryRepository.save(registry);

            if (!parsed.items().isEmpty()) {
                List<FiscalItem> items = new ArrayList<>();
                for (ParsedNfeItem parsedItem : parsed.items()) {
                    items.add(toFiscalItem(savedDoc, parsedItem));
                }
                fiscalItemRepository.saveAll(items);
            }
            if (!parsed.payments().isEmpty()) {
                List<FiscalDocumentPayment> payments = new ArrayList<>();
                for (ParsedNfePayment parsedPayment : parsed.payments()) {
                    payments.add(toFiscalDocumentPayment(savedDoc, parsedPayment));
                }
                fiscalDocumentPaymentRepository.saveAll(payments);
            }
            if (!parsed.duplicates().isEmpty()) {
                List<FiscalDocumentDuplicate> duplicates = new ArrayList<>();
                for (ParsedNfeDuplicate parsedDuplicate : parsed.duplicates()) {
                    duplicates.add(toFiscalDocumentDuplicate(savedDoc, parsedDuplicate));
                }
                fiscalDocumentDuplicateRepository.saveAll(duplicates);
            }
            if (!parsed.additionalInfos().isEmpty()) {
                List<FiscalDocumentAdditionalInfo> infos = new ArrayList<>();
                for (ParsedNfeAdditionalInfo parsedAdditionalInfo : parsed.additionalInfos()) {
                    infos.add(toFiscalDocumentAdditionalInfo(savedDoc, parsedAdditionalInfo));
                }
                fiscalDocumentAdditionalInfoRepository.saveAll(infos);
            }

            vincularEventosPendentes(savedDoc);

            item.setStatus(ImportItemStatus.PARSEADO);
            importItemRepository.save(item);
            successCounter.increment();
            finalizeImportacao(importacao, item, false);

            log.info("import.parse.success importacaoId={} importItemId={} correlationId={} accessKey={}",
                    importacao.getId(), item.getId(), correlationId, parsed.accessKey());
        } catch (DataIntegrityViolationException ex) {
            markItemDuplicado(importacao, item, correlationId, parsed.accessKey(), true);
        }
    }

    private void processEvent(Importacao importacao, ImportItem item, ParsedPayload payload, String correlationId) {
        ParsedNfeEvent parsed = payload.parsedEvent();
        applyItemMetadata(item, payload.xmlHash(), payload.xmlPath(), payload.xmlSize());
        item.setModel(parsed.model());
        item.setAccessKey(parsed.accessKey());
        if (parsed.eventDate() != null) {
            item.setIssueDate(parsed.eventDate());
        }

        Optional<FiscalDocumentEvent> existingEvent = fiscalDocumentEventRepository
                .findByTenantIdAndEmpresaIdAndEventId(importacao.getTenantId(), importacao.getEmpresaId(), parsed.eventId());
        if (existingEvent == null) {
            existingEvent = Optional.empty();
        }
        if (existingEvent.isPresent()) {
            vincularEventoExistente(existingEvent.get(), importacao.getTenantId(), importacao.getEmpresaId(), parsed.accessKey());
            markItemDuplicado(importacao, item, correlationId, parsed.accessKey(), false);
            return;
        }

        FiscalDocumentEvent event = new FiscalDocumentEvent();
        event.setTenantId(importacao.getTenantId());
        event.setEmpresaId(importacao.getEmpresaId());
        event.setAccessKey(parsed.accessKey());
        event.setModel(parsed.model());
        event.setEventId(parsed.eventId());
        event.setEventType(parsed.eventType());
        event.setEventDescription(parsed.eventDescription());
        event.setEventSequence(parsed.eventSequence());
        event.setEventDatetime(parsed.eventDatetime());
        event.setEventRegistrationDatetime(parsed.eventRegistrationDatetime());
        event.setEventStatus(parsed.eventStatus());
        event.setEventStatusReason(parsed.eventStatusReason());
        event.setEventProtocol(parsed.eventProtocol());
        event.setReferenceProtocol(parsed.referenceProtocol());
        event.setAuthorCnpj(parsed.authorCnpj());
        event.setXmlPath(resolveXmlPath(item, payload.xmlPath()));
        event.setXmlHash(payload.xmlHash());

        Optional<FiscalDocument> documentOpt = fiscalDocumentRepository
                .findByTenantIdAndEmpresaIdAndAccessKey(importacao.getTenantId(), importacao.getEmpresaId(), parsed.accessKey());
        documentOpt.ifPresent(event::setFiscalDocument);

        try {
            FiscalDocumentEvent savedEvent = fiscalDocumentEventRepository.save(event);
            if (documentOpt.isPresent()) {
                aplicarEventoNoDocumento(documentOpt.get(), savedEvent);
            }

            item.setStatus(ImportItemStatus.PARSEADO);
            importItemRepository.save(item);
            successCounter.increment();
            finalizeImportacao(importacao, item, false);

            log.info("import.parse.event.success importacaoId={} importItemId={} correlationId={} accessKey={} eventType={}",
                    importacao.getId(), item.getId(), correlationId, parsed.accessKey(), parsed.eventType());
        } catch (DataIntegrityViolationException ex) {
            markItemDuplicado(importacao, item, correlationId, parsed.accessKey(), true);
        }
    }

    private void applyItemMetadata(ImportItem item, String xmlHash, String xmlPath, long xmlSize) {
        item.setXmlHash(xmlHash);
        if (item.getXmlSize() == null || item.getXmlSize() <= 0) {
            item.setXmlSize(xmlSize);
        }
        if (!StringUtils.hasText(item.getXmlPath())) {
            item.setXmlPath(xmlPath);
        }
    }

    private void markItemDuplicado(Importacao importacao,
                                   ImportItem item,
                                   String correlationId,
                                   String accessKey,
                                   boolean raceCondition) {
        item.setStatus(ImportItemStatus.DUPLICADO);
        importItemRepository.save(item);
        duplicateCounter.increment();
        finalizeImportacao(importacao, item, false);
        if (raceCondition) {
            log.info("import.parse.duplicate_race importacaoId={} importItemId={} correlationId={} accessKey={}",
                    importacao.getId(), item.getId(), correlationId, accessKey);
        } else {
            log.info("import.parse.duplicate importacaoId={} importItemId={} correlationId={} accessKey={}",
                    importacao.getId(), item.getId(), correlationId, accessKey);
        }
    }

    private void vincularEventoExistente(FiscalDocumentEvent event, Long tenantId, Long empresaId, String accessKey) {
        if (event.getFiscalDocument() != null) {
            return;
        }
        Optional<FiscalDocument> documentOpt = fiscalDocumentRepository
                .findByTenantIdAndEmpresaIdAndAccessKey(tenantId, empresaId, accessKey);
        if (documentOpt.isEmpty()) {
            return;
        }
        event.setFiscalDocument(documentOpt.get());
        fiscalDocumentEventRepository.save(event);
        aplicarEventoNoDocumento(documentOpt.get(), event);
    }

    private void vincularEventosPendentes(FiscalDocument document) {
        List<FiscalDocumentEvent> events = fiscalDocumentEventRepository
                .findByTenantIdAndEmpresaIdAndAccessKeyOrderByCreatedAtAsc(
                        document.getTenantId(),
                        document.getEmpresaId(),
                        document.getAccessKey()
                );
        if (events == null || events.isEmpty()) {
            return;
        }
        for (FiscalDocumentEvent event : events) {
            if (event.getFiscalDocument() == null) {
                event.setFiscalDocument(document);
                fiscalDocumentEventRepository.save(event);
            }
            aplicarEventoNoDocumento(document, event);
        }
    }

    private void aplicarEventoNoDocumento(FiscalDocument document, FiscalDocumentEvent event) {
        if (!isCancelamento(event)) {
            return;
        }
        if (!isEventoCancelamentoAceito(event)) {
            return;
        }

        Instant cancelInstant = event.getEventDatetime() != null ? event.getEventDatetime() : Instant.now();
        if (document.getCancelledAt() != null && document.getCancelledAt().isAfter(cancelInstant)) {
            return;
        }

        document.setStatusDocumento(FiscalDocumentStatus.CANCELADA);
        document.setCancelledAt(cancelInstant);
        document.setCancelEvent(event);
        document.setCancelReason(firstNonBlank(event.getEventStatusReason(), event.getEventDescription()));
        document.setCancelProtocol(firstNonBlank(event.getEventProtocol(), event.getReferenceProtocol()));
        document.setCancelSequence(event.getEventSequence());
        fiscalDocumentRepository.save(document);
    }

    private boolean isCancelamento(FiscalDocumentEvent event) {
        return "110111".equals(event.getEventType());
    }

    private boolean isEventoCancelamentoAceito(FiscalDocumentEvent event) {
        Integer status = event.getEventStatus();
        return status != null && (status == 135 || status == 136 || status == 155);
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private ParsedPayload parsePayload(ParseXmlMessage message, ImportItem item) {
        if (StringUtils.hasText(message.zipEntryName())) {
            return parseZipEntry(message.objectKeyZip(), message.zipEntryName());
        }
        String objectKey = StringUtils.hasText(item.getStorageObjectKey())
                ? item.getStorageObjectKey()
                : message.objectKeyZip();
        if (!StringUtils.hasText(objectKey)) {
            throw new ValidationException("storage_object_key ausente");
        }
        return parseDirectObject(objectKey);
    }

    private ParsedPayload parseZipEntry(String zipObjectKey, String zipEntryName) {
        try (InputStream zipStream = storageService.get(zipObjectKey);
             ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (!entry.getName().equals(zipEntryName)) continue;
                return parseXmlStream(zis, zipEntryName);
            }
            throw new ValidationException("XML nao encontrado no zip");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new InfraException("Falha ao ler ZIP/XML", e);
        }
    }

    private ParsedPayload parseDirectObject(String objectKey) {
        try (InputStream xmlStream = storageService.get(objectKey)) {
            return parseXmlStream(xmlStream, objectKey);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new InfraException("Falha ao ler XML do storage", e);
        }
    }

    private ParsedPayload parseXmlStream(InputStream inputStream, String xmlPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        DigestInputStream dis = new DigestInputStream(countingInputStream, digest);
        BufferedInputStream buffered = new BufferedInputStream(dis, 128 * 1024);
        buffered.mark(128 * 1024);
        ParsedXmlType xmlType = detectXmlType(buffered);
        buffered.reset();

        ParsedNfe parsedNfe = null;
        ParsedNfeEvent parsedEvent = null;
        if (xmlType == ParsedXmlType.EVENT) {
            parsedEvent = nfeEventXmlParser.parse(buffered);
        } else {
            parsedNfe = nfeXmlParser.parse(buffered);
        }
        String xmlHash = bytesToHex(digest.digest());
        return new ParsedPayload(xmlType, parsedNfe, parsedEvent, xmlHash, xmlPath, countingInputStream.getCount());
    }

    private ParsedXmlType detectXmlType(InputStream inputStream) {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    String root = reader.getLocalName();
                    if ("procEventoNFe".equals(root) || "evento".equals(root)) {
                        return ParsedXmlType.EVENT;
                    }
                    if ("NFe".equals(root) || "nfeProc".equals(root)) {
                        return ParsedXmlType.DOCUMENT;
                    }
                    throw new ValidationException("xml_tipo_nao_suportado");
                }
            }
            throw new ValidationException("xml_vazio");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("xml_invalido");
        }
    }

    private void finalizeImportacao(Importacao importacao, ImportItem item, boolean error) {
        Long importacaoId = importacao.getId();
        Importacao lockedImportacao = importacao;

        // Acquire row lock before recomputing counters to prevent stale totals caused by concurrent parse commits.
        Optional<Importacao> lockedImportacaoOpt = importacaoRepository.findByIdForUpdate(importacaoId);
        if (lockedImportacaoOpt != null && lockedImportacaoOpt.isPresent()) {
            lockedImportacao = lockedImportacaoOpt.get();
        }

        long totalItems = importItemRepository.countByImportacaoId(importacaoId);
        long processed = importItemRepository.countByImportacaoIdAndStatusIn(importacaoId, PROCESSED_STATUSES);
        long errors = importItemRepository.countByImportacaoIdAndStatusIn(importacaoId, ERROR_STATUSES);

        lockedImportacao.setTotalProcessado((int) processed);
        lockedImportacao.setTotalErros((int) errors);

        if (totalItems > 0 && processed >= totalItems) {
            lockedImportacao.setStatus(ImportacaoStatus.CONCLUIDO);
        }
        importacaoRepository.save(lockedImportacao);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private FiscalItem toFiscalItem(FiscalDocument doc, ParsedNfeItem parsed) {
        FiscalItem item = new FiscalItem();
        item.setDocument(doc);
        item.setItemNumber(parsed.itemNumber());
        item.setProductCode(parsed.productCode());
        item.setProductDescription(parsed.productDescription());
        item.setNcm(parsed.ncm());
        item.setCfop(parsed.cfop());
        item.setCstIcms(parsed.cstIcms());
        item.setCsosn(parsed.csosn());
        item.setQuantity(parsed.quantity());
        item.setUnitPrice(parsed.unitPrice());
        item.setTotalValue(parsed.totalValue());
        item.setIcmsBase(parsed.icmsBase());
        item.setIcmsRate(parsed.icmsRate());
        item.setIcmsValue(parsed.icmsValue());
        item.setPisBase(parsed.pisBase());
        item.setPisRate(parsed.pisRate());
        item.setPisValue(parsed.pisValue());
        item.setCofinsBase(parsed.cofinsBase());
        item.setCofinsRate(parsed.cofinsRate());
        item.setCofinsValue(parsed.cofinsValue());
        return item;
    }

    private FiscalDocumentPayment toFiscalDocumentPayment(FiscalDocument doc, ParsedNfePayment parsed) {
        FiscalDocumentPayment payment = new FiscalDocumentPayment();
        payment.setDocument(doc);
        payment.setPaymentIndicator(parsed.paymentIndicator());
        payment.setPaymentType(parsed.paymentType());
        payment.setPaymentAmount(parsed.paymentAmount());
        payment.setCardIntegrationType(parsed.cardIntegrationType());
        payment.setCardCnpj(parsed.cardCnpj());
        payment.setCardBrand(parsed.cardBrand());
        payment.setCardAuthorizationCode(parsed.cardAuthorizationCode());
        return payment;
    }

    private FiscalDocumentDuplicate toFiscalDocumentDuplicate(FiscalDocument doc, ParsedNfeDuplicate parsed) {
        FiscalDocumentDuplicate duplicate = new FiscalDocumentDuplicate();
        duplicate.setDocument(doc);
        duplicate.setDuplicateNumber(parsed.duplicateNumber());
        duplicate.setDueDate(parsed.dueDate());
        duplicate.setAmount(parsed.amount());
        return duplicate;
    }

    private FiscalDocumentAdditionalInfo toFiscalDocumentAdditionalInfo(FiscalDocument doc, ParsedNfeAdditionalInfo parsed) {
        FiscalDocumentAdditionalInfo info = new FiscalDocumentAdditionalInfo();
        info.setDocument(doc);
        info.setInfoType(parsed.infoType());
        info.setFieldName(parsed.fieldName());
        info.setTextValue(parsed.textValue());
        return info;
    }

    private String resolveXmlPath(ImportItem item, String parsedPath) {
        if (StringUtils.hasText(item.getXmlPath())) return item.getXmlPath();
        if (StringUtils.hasText(item.getStorageObjectKey())) return item.getStorageObjectKey();
        return parsedPath;
    }

    private enum ParsedXmlType {
        DOCUMENT,
        EVENT
    }

    private record ParsedPayload(
            ParsedXmlType xmlType,
            ParsedNfe parsedNfe,
            ParsedNfeEvent parsedEvent,
            String xmlHash,
            String xmlPath,
            long xmlSize
    ) {}

    private static final class CountingInputStream extends FilterInputStream {
        private long count = 0L;

        private CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        public long getCount() {
            return count;
        }
    }
}
