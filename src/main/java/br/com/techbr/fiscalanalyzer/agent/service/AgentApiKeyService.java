package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AdminAgentKeyResponse;
import br.com.techbr.fiscalanalyzer.agent.dto.AdminCreateAgentKeyRequest;
import br.com.techbr.fiscalanalyzer.agent.model.AgentApiKey;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentApiKeyRepository;
import br.com.techbr.fiscalanalyzer.agent.security.AgentAuthContext;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class AgentApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(AgentApiKeyService.class);
    private static final String KEY_PREFIX = "fa_live_";
    private static final int RAW_BYTES = 32;
    private static final int PREFIX_SIZE = 12;

    private final AgentApiKeyRepository repository;
    private final TenantEmpresaValidationService tenantEmpresaValidationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentApiKeyService(AgentApiKeyRepository repository,
                              TenantEmpresaValidationService tenantEmpresaValidationService) {
        this.repository = repository;
        this.tenantEmpresaValidationService = tenantEmpresaValidationService;
    }

    @Transactional
    public AdminAgentKeyResponse createKey(Long tenantId, Long empresaId, AdminCreateAgentKeyRequest request) {
        if (tenantId == null || tenantId <= 0 || empresaId == null || empresaId <= 0) {
            throw new ValidationException("tenantId e empresaId sao obrigatorios");
        }
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);

        String plainKey = generateApiKey();
        String hash = sha256Hex(plainKey);
        String keyPrefix = plainKey.substring(0, Math.min(PREFIX_SIZE, plainKey.length()));

        AgentApiKey entity = new AgentApiKey();
        entity.setTenantId(tenantId);
        entity.setEmpresaId(empresaId);
        entity.setKeyHash(hash);
        entity.setKeyPrefix(keyPrefix);
        entity.setDescricao(request != null ? request.descricao() : null);
        entity.setCriadoPor(request != null ? request.criadoPor() : null);
        entity.setAtivo(true);

        AgentApiKey saved = repository.save(entity);
        return toResponse(saved, plainKey);
    }

    @Transactional(readOnly = true)
    public List<AdminAgentKeyResponse> listKeys(Long tenantId, Long empresaId) {
        if (tenantId == null || tenantId <= 0 || empresaId == null || empresaId <= 0) {
            throw new ValidationException("tenantId e empresaId sao obrigatorios");
        }
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        return repository.findByTenantIdAndEmpresaIdOrderByCriadoAtDesc(tenantId, empresaId).stream()
                .map(entity -> toResponse(entity, null))
                .toList();
    }

    @Transactional
    public void revokeKey(Long tenantId, Long empresaId, Long keyId) {
        if (tenantId == null || tenantId <= 0 || empresaId == null || empresaId <= 0 || keyId == null || keyId <= 0) {
            throw new ValidationException("tenantId, empresaId e keyId sao obrigatorios");
        }
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        AgentApiKey key = repository.findById(keyId)
                .orElseThrow(() -> new ValidationException("AgentApiKey nao encontrada: " + keyId));
        if (!tenantId.equals(key.getTenantId()) || !empresaId.equals(key.getEmpresaId())) {
            throw new ForbiddenException("ApiKey nao pertence ao tenant/empresa informado");
        }
        int updated = repository.revokeIfActive(tenantId, empresaId, keyId, Instant.now());
        if (updated == 0) {
            throw new ValidationException("ApiKey ja esta inativa/revogada");
        }
    }

    @Transactional
    public AdminAgentKeyResponse rotateKey(Long tenantId,
                                           Long empresaId,
                                           Long currentKeyId,
                                           boolean revokeCurrentKey,
                                           AdminCreateAgentKeyRequest request) {
        if (tenantId == null || tenantId <= 0 || empresaId == null || empresaId <= 0 || currentKeyId == null || currentKeyId <= 0) {
            throw new ValidationException("tenantId, empresaId e currentKeyId sao obrigatorios");
        }
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);

        AgentApiKey current = repository.findById(currentKeyId)
                .orElseThrow(() -> new ValidationException("AgentApiKey nao encontrada: " + currentKeyId));

        if (!tenantId.equals(current.getTenantId()) || !empresaId.equals(current.getEmpresaId())) {
            throw new ForbiddenException("ApiKey atual nao pertence ao tenant/empresa informado");
        }
        if (!Boolean.TRUE.equals(current.getAtivo()) || current.getRevogadoAt() != null) {
            throw new ValidationException("ApiKey atual ja esta inativa/revogada");
        }

        AdminAgentKeyResponse newKey = createKey(tenantId, empresaId, request);

        if (revokeCurrentKey) {
            int updated = repository.revokeIfActive(tenantId, empresaId, currentKeyId, Instant.now());
            if (updated == 0) {
                throw new ValidationException("ApiKey atual ja foi revogada/rotacionada em paralelo");
            }
        }

        log.info("agent.key.rotated tenantId={} empresaId={} oldKeyId={} newKeyId={} revokeOld={}",
                tenantId, empresaId, currentKeyId, newKey.id(), revokeCurrentKey);
        return newKey;
    }

    @Transactional
    public AgentAuthContext authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new UnauthorizedException("ApiKey ausente");
        }

        String hash = sha256Hex(rawApiKey);
        AgentApiKey key = repository.findByKeyHash(hash)
                .orElseThrow(() -> new UnauthorizedException("ApiKey invalida"));

        if (!Boolean.TRUE.equals(key.getAtivo()) || key.getRevogadoAt() != null) {
            throw new ForbiddenException("ApiKey revogada ou inativa");
        }

        tenantEmpresaValidationService.validateAtivo(key.getTenantId(), key.getEmpresaId());

        Instant now = Instant.now();
        Instant lastUse = key.getUltimoUsoAt();
        if (lastUse == null || lastUse.isBefore(now.minusSeconds(60))) {
            key.setUltimoUsoAt(now);
            repository.save(key);
        }

        return new AgentAuthContext(
                key.getId(),
                key.getTenantId(),
                key.getEmpresaId(),
                key.getKeyPrefix()
        );
    }

    private String generateApiKey() {
        byte[] bytes = new byte[RAW_BYTES];
        secureRandom.nextBytes(bytes);
        String random = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return KEY_PREFIX + random;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular hash da ApiKey", e);
        }
    }

    private AdminAgentKeyResponse toResponse(AgentApiKey entity, String apiKey) {
        return new AdminAgentKeyResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmpresaId(),
                entity.getKeyPrefix(),
                entity.getDescricao(),
                Boolean.TRUE.equals(entity.getAtivo()),
                entity.getUltimoUsoAt(),
                entity.getCriadoAt(),
                entity.getRevogadoAt(),
                apiKey
        );
    }
}
