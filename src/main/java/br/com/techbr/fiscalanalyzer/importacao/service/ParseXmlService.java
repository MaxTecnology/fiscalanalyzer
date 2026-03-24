package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentRegistry;
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
import br.com.techbr.fiscalanalyzer.xml.parser.NfeXmlParser;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfe;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeItem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.EnumSet;
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
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );
    private static final Set<ImportItemStatus> NON_FINAL_STATUSES = EnumSet.of(
            ImportItemStatus.PENDENTE,
            ImportItemStatus.PENDENTE_PARSE,
            ImportItemStatus.PROCESSANDO
    );

    private final ImportacaoRepository importacaoRepository;
    private final ImportItemRepository importItemRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentRegistryRepository registryRepository;
    private final FiscalItemRepository fiscalItemRepository;
    private final StorageService storageService;
    private final NfeXmlParser nfeXmlParser;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter duplicateCounter;
    private final Timer parseTimer;
    private final MeterRegistry meterRegistry;

    public ParseXmlService(ImportacaoRepository importacaoRepository,
                           ImportItemRepository importItemRepository,
                           FiscalDocumentRepository fiscalDocumentRepository,
                           FiscalDocumentRegistryRepository registryRepository,
                           FiscalItemRepository fiscalItemRepository,
                           StorageService storageService,
                           MeterRegistry meterRegistry) {
        this.importacaoRepository = importacaoRepository;
        this.importItemRepository = importItemRepository;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.registryRepository = registryRepository;
        this.fiscalItemRepository = fiscalItemRepository;
        this.storageService = storageService;
        this.nfeXmlParser = new NfeXmlParser();
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
            ParsedNfe parsed = payload.parsed();
            String xmlHash = payload.xmlHash();

            item.setModel(parsed.model());
            item.setAccessKey(parsed.accessKey());
            item.setIssueDate(parsed.issueDate());
            item.setXmlHash(xmlHash);
            if (!StringUtils.hasText(item.getXmlPath())) {
                item.setXmlPath(payload.xmlPath());
            }

            Optional<FiscalDocumentRegistry> existing = registryRepository
                    .findByTenantIdAndEmpresaIdAndAccessKey(importacao.getTenantId(), importacao.getEmpresaId(), parsed.accessKey());

            if (existing.isPresent()) {
                item.setStatus(ImportItemStatus.DUPLICADO);
                importItemRepository.save(item);
                duplicateCounter.increment();
                finalizeImportacao(importacao, item, false);
                log.info("import.parse.duplicate importacaoId={} importItemId={} correlationId={} accessKey={}",
                        importacao.getId(), item.getId(), correlationId, parsed.accessKey());
                return;
            }

            FiscalDocument doc = new FiscalDocument();
            doc.setTenantId(importacao.getTenantId());
            doc.setEmpresaId(importacao.getEmpresaId());
            doc.setModel(parsed.model());
            doc.setAccessKey(parsed.accessKey());
            doc.setIssueDate(parsed.issueDate());
            doc.setIssueDateTime(parsed.issueDateTime());
            doc.setOperationType(parsed.operationType());
            doc.setEmitCnpj(parsed.emitCnpj());
            doc.setDestCnpj(parsed.destCnpj());
            doc.setTotalProducts(parsed.totalProducts());
            doc.setTotalAmount(parsed.totalAmount());
            doc.setTotalIcms(parsed.totalIcms());
            doc.setTotalPis(parsed.totalPis());
            doc.setTotalCofins(parsed.totalCofins());
            doc.setImportacao(importacao);
            doc.setXmlPath(resolveXmlPath(item, payload.xmlPath()));
            doc.setXmlHash(xmlHash);

            FiscalDocument savedDoc = fiscalDocumentRepository.save(doc);

            try {
                FiscalDocumentRegistry registry = new FiscalDocumentRegistry();
                registry.setTenantId(importacao.getTenantId());
                registry.setEmpresaId(importacao.getEmpresaId());
                registry.setAccessKey(parsed.accessKey());
                registry.setFiscalDocument(savedDoc);
                registryRepository.save(registry);

                if (!parsed.items().isEmpty()) {
                    java.util.List<FiscalItem> items = new java.util.ArrayList<>();
                    for (ParsedNfeItem parsedItem : parsed.items()) {
                        items.add(toFiscalItem(savedDoc, parsedItem));
                    }
                    fiscalItemRepository.saveAll(items);
                }

                item.setStatus(ImportItemStatus.PARSEADO);
                importItemRepository.save(item);
                successCounter.increment();
                finalizeImportacao(importacao, item, false);

                log.info("import.parse.success importacaoId={} importItemId={} correlationId={} accessKey={}",
                        importacao.getId(), item.getId(), correlationId, parsed.accessKey());
                return;
            } catch (DataIntegrityViolationException ex) {
                item.setStatus(ImportItemStatus.DUPLICADO);
                importItemRepository.save(item);
                duplicateCounter.increment();
                finalizeImportacao(importacao, item, false);

                log.info("import.parse.duplicate_race importacaoId={} importItemId={} correlationId={} accessKey={}",
                        importacao.getId(), item.getId(), correlationId, parsed.accessKey());
                return;
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
        DigestInputStream dis = new DigestInputStream(inputStream, digest);
        ParsedNfe parsed = nfeXmlParser.parse(dis);
        String xmlHash = bytesToHex(digest.digest());
        return new ParsedPayload(parsed, xmlHash, xmlPath);
    }

    private void finalizeImportacao(Importacao importacao, ImportItem item, boolean error) {
        importacao.setTotalProcessado(importacao.getTotalProcessado() + 1);
        if (error) {
            importacao.setTotalErros(importacao.getTotalErros() + 1);
        }
        long remaining = importItemRepository.countByImportacaoIdAndStatusIn(
                importacao.getId(),
                NON_FINAL_STATUSES
        );
        if (remaining == 0) {
            importacao.setStatus(ImportacaoStatus.CONCLUIDO);
        }
        importacaoRepository.save(importacao);
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

    private String resolveXmlPath(ImportItem item, String parsedPath) {
        if (StringUtils.hasText(item.getXmlPath())) return item.getXmlPath();
        if (StringUtils.hasText(item.getStorageObjectKey())) return item.getStorageObjectKey();
        return parsedPath;
    }

    private record ParsedPayload(ParsedNfe parsed, String xmlHash, String xmlPath) {}
}
