package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Service
public class ExtractZipService {

    private static final Logger log = LoggerFactory.getLogger(ExtractZipService.class);

    private final ImportacaoRepository importacaoRepository;
    private final ImportItemRepository importItemRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Counter itemsCreatedCounter;
    private final Timer extractionTimer;

    public ExtractZipService(ImportacaoRepository importacaoRepository,
                             ImportItemRepository importItemRepository,
                             StorageService storageService,
                             ApplicationEventPublisher eventPublisher,
                             MeterRegistry meterRegistry) {
        this.importacaoRepository = importacaoRepository;
        this.importItemRepository = importItemRepository;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.itemsCreatedCounter = meterRegistry.counter("import.extract.items.created");
        this.extractionTimer = Timer.builder("import.extract.duration").register(meterRegistry);
    }

    @Transactional
    public void process(ExtractZipMessage message, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        int xmlCount = 0;
        int created = 0;

        log.info("import.extract.started importacaoId={} correlationId={} bucket={} key={}",
                message.importacaoId(), correlationId, message.bucket(), message.objectKey());

        Importacao importacao = importacaoRepository.findById(message.importacaoId())
                .orElseThrow(() -> new ValidationException("Importacao nao encontrada: " + message.importacaoId()));

        importacao.setStatus(ImportacaoStatus.EM_EXTRACAO);
        importacao.setErroCodigo(null);
        importacao.setErroMensagem(null);
        importacaoRepository.save(importacao);

        try (InputStream in = storageService.get(message.objectKey());
             ZipInputStream zis = new ZipInputStream(in)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    continue;
                }
                xmlCount++;
                Long size = entry.getSize() >= 0 ? entry.getSize() : null;

                ItemResult result = getOrCreateItem(importacao, entryName, size);
                if (result == null || result.item() == null) {
                    continue;
                }

                ImportItem item = result.item();
                if (item.getStatus() == ImportItemStatus.PENDENTE || item.getStatus() == ImportItemStatus.PENDENTE_PARSE) {
                    eventPublisher.publishEvent(new ParseXmlRequestedEvent(
                            importacao.getId(),
                            item.getId(),
                            message.bucket(),
                            message.objectKey(),
                            entryName,
                            message.sha256(),
                            correlationId
                    ));
                    if (result.created()) {
                        created++;
                    }
                }
            }

        } catch (ZipException e) {
            markFailure(importacao, "ZIP_INVALIDO", e.getMessage());
            log.warn("import.extract.invalid_zip importacaoId={} correlationId={} message={}",
                    importacao.getId(), correlationId, e.getMessage());
            return;
        } catch (IOException e) {
            throw new InfraException("Falha ao ler ZIP", e);
        } finally {
            sample.stop(extractionTimer);
        }

        importacao.setTotalEncontrado(xmlCount);
        importacao.setStatus(ImportacaoStatus.EXTRAIDO);
        importacaoRepository.save(importacao);
        itemsCreatedCounter.increment(created);

        log.info("import.extract.completed importacaoId={} correlationId={} xmlCount={} itemsPublished={}",
                importacao.getId(), correlationId, xmlCount, created);
    }

    private ItemResult getOrCreateItem(Importacao importacao, String entryName, Long size) {
        Optional<ImportItem> existing = importItemRepository.findByImportacaoIdAndXmlPath(importacao.getId(), entryName);
        if (existing.isPresent()) {
            return new ItemResult(existing.get(), false);
        }

        ImportItem item = new ImportItem();
        item.setImportacao(importacao);
        item.setXmlPath(entryName);
        item.setXmlSize(size);
        item.setStatus(ImportItemStatus.PENDENTE_PARSE);

        try {
            return new ItemResult(importItemRepository.save(item), true);
        } catch (DataIntegrityViolationException ex) {
            return importItemRepository.findByImportacaoIdAndXmlPath(importacao.getId(), entryName)
                    .map(i -> new ItemResult(i, false))
                    .orElse(null);
        }
    }

    private void markFailure(Importacao importacao, String code, String message) {
        importacao.setStatus(ImportacaoStatus.FALHA);
        importacao.setErroCodigo(code);
        importacao.setErroMensagem(message);
        importacaoRepository.save(importacao);
    }

    private record ItemResult(ImportItem item, boolean created) {}
}
