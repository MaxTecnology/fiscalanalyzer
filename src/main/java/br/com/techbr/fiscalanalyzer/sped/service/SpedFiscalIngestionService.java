package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.sped.dto.SpedUploadResponse;
import br.com.techbr.fiscalanalyzer.sped.event.ParseSpedRequestedEvent;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileSourceType;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SpedFiscalIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SpedFiscalIngestionService.class);
    private static final String INPUT_TXT = "TXT";
    private static final String INPUT_ZIP = "ZIP";
    private static final String TXT_CONTENT_TYPE = "text/plain";

    private final SpedFiscalFileRepository spedFiscalFileRepository;
    private final StorageService storageService;
    private final TenantEmpresaValidationService tenantEmpresaValidationService;
    private final ApplicationEventPublisher eventPublisher;
    private final long maxUploadBytes;
    private final long maxTxtBytes;
    private final String bucket;

    public SpedFiscalIngestionService(SpedFiscalFileRepository spedFiscalFileRepository,
                                      StorageService storageService,
                                      TenantEmpresaValidationService tenantEmpresaValidationService,
                                      ApplicationEventPublisher eventPublisher,
                                      @Value("${app.sped.max-upload-size:300MB}") DataSize maxUploadSize,
                                      @Value("${app.sped.max-txt-size:50MB}") DataSize maxTxtSize,
                                      @Value("${storage.s3.bucket}") String bucket) {
        this.spedFiscalFileRepository = spedFiscalFileRepository;
        this.storageService = storageService;
        this.tenantEmpresaValidationService = tenantEmpresaValidationService;
        this.eventPublisher = eventPublisher;
        this.maxUploadBytes = maxUploadSize.toBytes();
        this.maxTxtBytes = maxTxtSize.toBytes();
        this.bucket = bucket;
    }

    @Transactional
    public SpedUploadResponse upload(Long tenantId, Long empresaId, MultipartFile file) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        validateUploadFile(file);

        String originalName = sanitizeName(file.getOriginalFilename());
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".txt")) {
            return processTxt(tenantId, empresaId, file, originalName);
        }
        if (lowerName.endsWith(".zip")) {
            return processZip(tenantId, empresaId, file, originalName);
        }
        throw new ValidationException("Formato nao suportado. Use .txt ou .zip");
    }

    private SpedUploadResponse processTxt(Long tenantId, Long empresaId, MultipartFile file, String originalName) {
        byte[] content = readAllBytes(file);
        if (content.length == 0) {
            throw new ValidationException("Arquivo SPED vazio");
        }
        if (content.length > maxTxtBytes) {
            throw new ValidationException("Arquivo SPED excede tamanho maximo permitido");
        }

        String hash = sha256Hex(content);
        String objectKey = "sped/%d/%d/%s.txt".formatted(tenantId, empresaId, hash);
        String correlationId = UUID.randomUUID().toString();
        int duplicated = 0;
        int accepted = 0;
        int queued = 0;

        if (isDuplicate(tenantId, empresaId, hash)) {
            duplicated = 1;
        } else {
            SpedFiscalFile persisted = saveSpedFile(tenantId, empresaId, SpedFiscalFileSourceType.TXT, originalName,
                    objectKey, null, hash, (long) content.length, TXT_CONTENT_TYPE);
            try {
                storageService.put(objectKey, new ByteArrayInputStream(content), content.length, TXT_CONTENT_TYPE);
            } catch (Exception e) {
                throw new InfraException("Falha ao salvar arquivo SPED no storage", e);
            }
            publishParseEvent(persisted, correlationId);
            accepted = 1;
            queued = 1;
        }

        log.info("sped.upload.accepted tenantId={} empresaId={} inputType={} totalCandidates={} acceptedFiles={} duplicatedFiles={} queuedFiles={}",
                tenantId, empresaId, INPUT_TXT, 1, accepted, duplicated, queued);

        return new SpedUploadResponse(INPUT_TXT, 1, accepted, duplicated, queued);
    }

    private SpedUploadResponse processZip(Long tenantId, Long empresaId, MultipartFile file, String originalName) {
        String zipHash = sha256Hex(file);
        String zipObjectKey = "sped/%d/%d/uploads/%s.zip".formatted(tenantId, empresaId, zipHash);
        List<ZipTxtCandidate> candidates = parseZipCandidates(file);
        if (candidates.isEmpty()) {
            throw new ValidationException("ZIP sem arquivos .txt de SPED");
        }

        try {
            storageService.put(zipObjectKey, file.getInputStream(), file.getSize(), "application/zip");
        } catch (Exception e) {
            throw new InfraException("Falha ao salvar ZIP SPED no storage", e);
        }

        String correlationId = UUID.randomUUID().toString();
        int accepted = 0;
        int duplicated = 0;
        int queued = 0;

        for (ZipTxtCandidate candidate : candidates) {
            if (isDuplicate(tenantId, empresaId, candidate.hashSha256())) {
                duplicated++;
                continue;
            }

            SpedFiscalFile persisted = saveSpedFile(
                    tenantId,
                    empresaId,
                    SpedFiscalFileSourceType.ZIP_ENTRY,
                    candidate.fileName(),
                    zipObjectKey,
                    candidate.entryName(),
                    candidate.hashSha256(),
                    candidate.contentSize(),
                    TXT_CONTENT_TYPE
            );
            publishParseEvent(persisted, correlationId);
            accepted++;
            queued++;
        }

        log.info("sped.upload.accepted tenantId={} empresaId={} inputType={} zipName={} totalCandidates={} acceptedFiles={} duplicatedFiles={} queuedFiles={} zipKey={}",
                tenantId, empresaId, INPUT_ZIP, originalName, candidates.size(), accepted, duplicated, queued, zipObjectKey);

        return new SpedUploadResponse(INPUT_ZIP, candidates.size(), accepted, duplicated, queued);
    }

    private List<ZipTxtCandidate> parseZipCandidates(MultipartFile file) {
        List<ZipTxtCandidate> candidates = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = sanitizeName(entry.getName());
                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                    continue;
                }
                byte[] entryContent = readZipEntry(zis);
                if (entryContent.length == 0) {
                    continue;
                }
                String hash = sha256Hex(entryContent);
                candidates.add(new ZipTxtCandidate(entryName, fileNameFromPath(entryName), hash, (long) entryContent.length));
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("ZIP invalido para importacao de SPED");
        }
        return candidates;
    }

    private byte[] readZipEntry(ZipInputStream zis) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            long total = 0L;
            while ((read = zis.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxTxtBytes) {
                    throw new ValidationException("Arquivo SPED no ZIP excede tamanho maximo permitido");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Falha ao ler entrada do ZIP SPED");
        }
    }

    private SpedFiscalFile saveSpedFile(Long tenantId,
                                        Long empresaId,
                                        SpedFiscalFileSourceType sourceType,
                                        String originalFileName,
                                        String objectKey,
                                        String zipEntryName,
                                        String hashSha256,
                                        Long contentSize,
                                        String contentType) {
        SpedFiscalFile entity = new SpedFiscalFile();
        entity.setTenantId(tenantId);
        entity.setEmpresaId(empresaId);
        entity.setSourceType(sourceType);
        entity.setStatus(SpedFiscalFileStatus.PENDENTE_PARSE);
        entity.setOriginalFileName(originalFileName);
        entity.setStorageObjectKey(objectKey);
        entity.setZipEntryName(zipEntryName);
        entity.setHashSha256(hashSha256);
        entity.setContentSize(contentSize);
        entity.setContentType(contentType);
        try {
            return spedFiscalFileRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            return spedFiscalFileRepository.findByTenantIdAndEmpresaIdAndHashSha256(tenantId, empresaId, hashSha256)
                    .orElseThrow(() -> ex);
        }
    }

    private boolean isDuplicate(Long tenantId, Long empresaId, String hash) {
        return spedFiscalFileRepository.findByTenantIdAndEmpresaIdAndHashSha256(tenantId, empresaId, hash).isPresent();
    }

    private void publishParseEvent(SpedFiscalFile spedFiscalFile, String correlationId) {
        eventPublisher.publishEvent(new ParseSpedRequestedEvent(
                spedFiscalFile.getId(),
                bucket,
                spedFiscalFile.getStorageObjectKey(),
                spedFiscalFile.getZipEntryName(),
                spedFiscalFile.getHashSha256(),
                correlationId
        ));
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Arquivo SPED obrigatorio");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ValidationException("Arquivo excede o tamanho maximo permitido");
        }
        String originalName = sanitizeName(file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".txt")
                && !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ValidationException("Formato nao suportado. Use .txt ou .zip");
        }
    }

    private byte[] readAllBytes(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new InfraException("Falha ao ler arquivo SPED", e);
        }
    }

    private String sha256Hex(MultipartFile file) {
        try (var in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new InfraException("Falha ao calcular hash do arquivo SPED", e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new InfraException("Falha ao calcular hash do conteudo SPED", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sanitizeName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "sped.txt";
        }
        return fileName.trim().replace('\\', '/');
    }

    private String fileNameFromPath(String path) {
        try {
            return Paths.get(path).getFileName().toString();
        } catch (Exception e) {
            return path;
        }
    }

    private record ZipTxtCandidate(String entryName, String fileName, String hashSha256, Long contentSize) {
    }
}

