package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestEntryRequest;
import br.com.techbr.fiscalanalyzer.importacao.dto.ManifestRequest;
import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.queue.producer.ExtractZipProducer;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "multipart/x-zip",
            "application/octet-stream"
    );
    private static final String PENDING_PATH = "pending";
    private static final String MANIFEST_FILENAME = "manifest.json";
    private static final String MANIFEST_CONTENT_TYPE = "application/json";

    private final ImportacaoRepository importacaoRepository;
    private final ImportItemRepository importItemRepository;
    private final StorageService storageService;
    private final ExtractZipProducer extractZipProducer;
    private final ApplicationEventPublisher eventPublisher;
    private final long maxZipSizeBytes;
    private final String bucket;

    public ImportacaoService(ImportacaoRepository importacaoRepository,
                             ImportItemRepository importItemRepository,
                             StorageService storageService,
                             ExtractZipProducer extractZipProducer,
                             ApplicationEventPublisher eventPublisher,
                             @Value("${app.importacao.max-zip-size}") DataSize maxZipSize,
                             @Value("${storage.s3.bucket}") String bucket) {
        this.importacaoRepository = importacaoRepository;
        this.importItemRepository = importItemRepository;
        this.storageService = storageService;
        this.extractZipProducer = extractZipProducer;
        this.eventPublisher = eventPublisher;
        this.maxZipSizeBytes = maxZipSize.toBytes();
        this.bucket = bucket;
    }

    @Transactional
    public Importacao criarImportacao(Long tenantId, Long empresaId, MultipartFile zipFile) {
        if (tenantId == null || empresaId == null) {
            throw new ValidationException("tenantId e empresaId sao obrigatorios");
        }
        validateZip(zipFile);

        String sha256 = sha256Hex(zipFile);
        long size = zipFile.getSize();
        String contentType = resolveContentType(zipFile);

        log.info("import.upload.received tenantId={} empresaId={} filename={} size={} contentType={}",
                tenantId, empresaId, zipFile.getOriginalFilename(), size, contentType);

        Importacao imp = new Importacao();
        imp.setTenantId(tenantId);
        imp.setEmpresaId(empresaId);
        imp.setStatus(ImportacaoStatus.RECEBIDO);
        imp.setSourceType(ImportacaoSourceType.ZIP);
        imp.setArquivoNome(zipFile.getOriginalFilename() != null ? zipFile.getOriginalFilename() : "upload.zip");
        imp.setArquivoHash(sha256);
        imp.setArquivoTamanho(size);
        imp.setArquivoContentType(contentType);
        imp.setArquivoPath(PENDING_PATH);
        imp.setTotalEncontrado(0);
        imp.setTotalProcessado(0);
        imp.setTotalErros(0);

        imp = importacaoRepository.save(imp);

        String objectKey = "imports/%d/%s.zip".formatted(imp.getId(), sha256);
        try {
            storageService.put(objectKey, zipFile.getInputStream(), size, contentType);
        } catch (Exception e) {
            log.error("import.upload.storage_error importacaoId={} key={} message={}",
                    imp.getId(), objectKey, e.getMessage(), e);
            throw new InfraException("Falha ao salvar ZIP no storage", e);
        }

        imp.setArquivoPath(objectKey);
        imp = importacaoRepository.save(imp);

        try {
            extractZipProducer.send(new ExtractZipMessage(
                    imp.getId(),
                    bucket,
                    objectKey,
                    sha256
            ));
        } catch (Exception e) {
            log.error("import.upload.queue_error importacaoId={} key={} message={}",
                    imp.getId(), objectKey, e.getMessage(), e);
            throw new InfraException("Falha ao publicar mensagem de extracao", e);
        }

        log.info("import.upload.completed importacaoId={} key={} sha256={}", imp.getId(), objectKey, sha256);
        return imp;
    }

    @Transactional
    public Importacao criarImportacaoPorManifesto(ManifestRequest request) {
        if (request == null) {
            throw new ValidationException("Manifesto obrigatorio");
        }
        if (request.tenantId() == null || request.empresaId() == null) {
            throw new ValidationException("tenantId e empresaId sao obrigatorios");
        }

        List<ManifestEntryRequest> entries = normalizeManifestEntries(request.entries());
        validateManifestObjectsExist(entries);

        String manifestHash = manifestHash(entries);
        long totalSize = entries.stream()
                .map(ManifestEntryRequest::sizeBytes)
                .filter(size -> size != null && size > 0)
                .mapToLong(Long::longValue)
                .sum();

        Importacao importacao = new Importacao();
        importacao.setTenantId(request.tenantId());
        importacao.setEmpresaId(request.empresaId());
        importacao.setStatus(ImportacaoStatus.RECEBIDO);
        importacao.setSourceType(ImportacaoSourceType.MANIFEST);
        importacao.setArquivoNome(MANIFEST_FILENAME);
        importacao.setArquivoPath(PENDING_PATH);
        importacao.setArquivoHash(manifestHash);
        importacao.setArquivoTamanho(totalSize > 0 ? totalSize : null);
        importacao.setArquivoContentType(MANIFEST_CONTENT_TYPE);
        importacao.setTotalEncontrado(entries.size());
        importacao.setTotalProcessado(0);
        importacao.setTotalErros(0);

        importacao = importacaoRepository.save(importacao);

        String manifestPath = "manifests/%d/%s.json".formatted(importacao.getId(), manifestHash);
        importacao.setArquivoPath(manifestPath);
        importacao = importacaoRepository.save(importacao);

        String correlationId = UUID.randomUUID().toString();
        int published = 0;

        for (ManifestEntryRequest entry : entries) {
            ImportItem item = getOrCreateManifestItem(importacao, entry);
            if (item.getStatus() == ImportItemStatus.PENDENTE || item.getStatus() == ImportItemStatus.PENDENTE_PARSE) {
                eventPublisher.publishEvent(new ParseXmlRequestedEvent(
                        importacao.getId(),
                        item.getId(),
                        bucket,
                        entry.objectKey(),
                        null,
                        entry.sha256(),
                        correlationId
                ));
                published++;
            }
        }

        log.info("import.manifest.accepted importacaoId={} tenantId={} empresaId={} totalItems={} eventsPublished={} correlationId={}",
                importacao.getId(), request.tenantId(), request.empresaId(), entries.size(), published, correlationId);

        return importacao;
    }

    private ImportItem getOrCreateManifestItem(Importacao importacao, ManifestEntryRequest entry) {
        return importItemRepository.findByImportacaoIdAndXmlPath(importacao.getId(), entry.objectKey())
                .orElseGet(() -> {
                    ImportItem item = new ImportItem();
                    item.setImportacao(importacao);
                    item.setXmlPath(entry.objectKey());
                    item.setStorageObjectKey(entry.objectKey());
                    item.setXmlHash(entry.sha256());
                    item.setXmlSize(entry.sizeBytes());
                    item.setStatus(ImportItemStatus.PENDENTE_PARSE);
                    try {
                        return importItemRepository.save(item);
                    } catch (DataIntegrityViolationException ex) {
                        return importItemRepository.findByImportacaoIdAndXmlPath(importacao.getId(), entry.objectKey())
                                .orElseThrow(() -> ex);
                    }
                });
    }

    private void validateManifestObjectsExist(List<ManifestEntryRequest> entries) {
        for (ManifestEntryRequest entry : entries) {
            boolean exists;
            try {
                exists = storageService.exists(entry.objectKey());
            } catch (Exception e) {
                throw new InfraException("Falha ao verificar objeto no storage: " + entry.objectKey(), e);
            }
            if (!exists) {
                throw new UnprocessableEntityException("objectKey nao encontrado no storage: " + entry.objectKey());
            }
        }
    }

    private List<ManifestEntryRequest> normalizeManifestEntries(List<ManifestEntryRequest> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new ValidationException("Manifesto sem entries");
        }
        List<ManifestEntryRequest> normalized = new ArrayList<>(entries.size());
        Set<String> keys = new HashSet<>();

        for (ManifestEntryRequest entry : entries) {
            if (entry == null) {
                throw new ValidationException("Entry do manifesto invalida");
            }
            String objectKey = trimToNull(entry.objectKey());
            if (objectKey == null) {
                throw new ValidationException("objectKey obrigatorio");
            }
            String sha256 = trimToNull(entry.sha256());
            if (sha256 == null || !sha256.matches("(?i)^[a-f0-9]{64}$")) {
                throw new ValidationException("sha256 invalido para objectKey: " + objectKey);
            }
            Long size = entry.sizeBytes();
            if (size != null && size < 0) {
                throw new ValidationException("sizeBytes invalido para objectKey: " + objectKey);
            }
            if (!keys.add(objectKey)) {
                throw new ValidationException("objectKey duplicado no manifesto: " + objectKey);
            }
            normalized.add(new ManifestEntryRequest(
                    objectKey,
                    sha256.toLowerCase(Locale.ROOT),
                    size,
                    trimToNull(entry.originalFileName())
            ));
        }

        return normalized;
    }

    private String manifestHash(List<ManifestEntryRequest> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            entries.stream()
                    .sorted(Comparator.comparing(ManifestEntryRequest::objectKey))
                    .forEach(entry -> {
                        String line = entry.objectKey() + "|" + entry.sha256() + "|" + (entry.sizeBytes() == null ? "" : entry.sizeBytes());
                        md.update(line.getBytes(StandardCharsets.UTF_8));
                        md.update((byte) '\n');
                    });
            return bytesToHex(md.digest());
        } catch (Exception e) {
            throw new InfraException("Falha ao calcular hash do manifesto", e);
        }
    }

    private String bytesToHex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateZip(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Arquivo ZIP vazio");
        }
        if (file.getSize() > maxZipSizeBytes) {
            throw new ValidationException("Arquivo excede o tamanho maximo permitido");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".zip")) {
            throw new ValidationException("Somente .zip e suportado no upload");
        }
        String contentType = resolveContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("Content-Type invalido para ZIP: " + contentType);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/zip";
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var is = file.getInputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) > 0) md.update(buf, 0, r);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new InfraException("Falha ao calcular hash do arquivo", e);
        }
    }

    @Transactional(readOnly = true)
    public Importacao buscarPorId(Long id) {
        return importacaoRepository.findById(id)
                .orElseThrow(() -> new ValidationException("Importacao nao encontrada: " + id));
    }

}
