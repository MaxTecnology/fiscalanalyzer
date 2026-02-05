package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.queue.producer.ExtractZipProducer;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

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

    private final ImportacaoRepository importacaoRepository;
    private final StorageService storageService;
    private final ExtractZipProducer extractZipProducer;
    private final long maxZipSizeBytes;
    private final String bucket;

    public ImportacaoService(ImportacaoRepository importacaoRepository,
                             StorageService storageService,
                             ExtractZipProducer extractZipProducer,
                             @Value("${app.importacao.max-zip-size}") DataSize maxZipSize,
                             @Value("${storage.s3.bucket}") String bucket) {
        this.importacaoRepository = importacaoRepository;
        this.storageService = storageService;
        this.extractZipProducer = extractZipProducer;
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
