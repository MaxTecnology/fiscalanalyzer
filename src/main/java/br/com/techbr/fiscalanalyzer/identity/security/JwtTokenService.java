package br.com.techbr.fiscalanalyzer.identity.security;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALG = "HS256";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();
    private final long accessTokenTtlSeconds;
    private final byte[] secretBytes;
    private final boolean configured;

    public JwtTokenService(ObjectMapper objectMapper,
                           @Value("${app.security.jwt.secret:}") String secret,
                           @Value("${app.security.jwt.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds) {
        this.objectMapper = objectMapper;
        String normalizedSecret = secret == null ? "" : secret.trim();
        this.secretBytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);
        this.configured = normalizedSecret.length() >= MIN_SECRET_LENGTH;
        this.accessTokenTtlSeconds = Math.max(60, accessTokenTtlSeconds);
    }

    public AccessToken issueAccessToken(Long userId, String email, List<String> roles) {
        ensureConfigured();
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("userId invalido para emissao de token");
        }
        if (!StringUtils.hasText(email)) {
            throw new UnauthorizedException("email invalido para emissao de token");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

        Map<String, Object> header = Map.of(
                "alg", JWT_ALG,
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "fiscalanalyzer");
        payload.put("aud", "fiscalanalyzer-admin");
        payload.put("sub", String.valueOf(userId));
        payload.put("email", email.toLowerCase(Locale.ROOT));
        payload.put("roles", roles == null ? List.of() : roles);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = encodeMap(header);
        String encodedPayload = encodeMap(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);
        return new AccessToken(signingInput + "." + signature, expiresAt);
    }

    public JwtPrincipal parseAndValidateAccessToken(String token) {
        ensureConfigured();
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException("JWT ausente");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Formato de JWT invalido");
        }

        byte[] expectedSignature = decodeBase64Url(sign(parts[0] + "." + parts[1]));
        byte[] providedSignature = decodeBase64Url(parts[2]);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new UnauthorizedException("Assinatura JWT invalida");
        }

        Map<String, Object> header = decodeMap(parts[0]);
        Object alg = header.get("alg");
        if (!JWT_ALG.equals(alg)) {
            throw new UnauthorizedException("Algoritmo JWT invalido");
        }

        Map<String, Object> payload = decodeMap(parts[1]);
        long exp = asEpochSecond(payload.get("exp"), "exp");
        Instant expiresAt = Instant.ofEpochSecond(exp);
        if (Instant.now().isAfter(expiresAt)) {
            throw new UnauthorizedException("JWT expirado");
        }

        long userId = asPositiveLong(payload.get("sub"), "sub");
        String email = asText(payload.get("email"), "email");
        List<String> roles = asRoleList(payload.get("roles"));

        return new JwtPrincipal(userId, email, roles, expiresAt);
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new InfraException(
                    "JWT nao configurado: APP_SECURITY_JWT_SECRET deve possuir no minimo 32 caracteres",
                    new IllegalStateException("jwt.secret.missing_or_too_short")
            );
        }
    }

    private String encodeMap(Map<String, Object> data) {
        try {
            byte[] rawJson = objectMapper.writeValueAsBytes(data);
            return urlEncoder.encodeToString(rawJson);
        } catch (Exception e) {
            throw new InfraException("Falha ao serializar JWT", e);
        }
    }

    private Map<String, Object> decodeMap(String part) {
        try {
            byte[] raw = decodeBase64Url(part);
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Conteudo JWT invalido");
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return urlEncoder.encodeToString(signature);
        } catch (Exception e) {
            throw new InfraException("Falha ao assinar JWT", e);
        }
    }

    private byte[] decodeBase64Url(String value) {
        try {
            return urlDecoder.decode(value);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Base64 URL invalido no JWT");
        }
    }

    private long asEpochSecond(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                throw new UnauthorizedException("Campo JWT invalido: " + field);
            }
        }
        throw new UnauthorizedException("Campo JWT ausente: " + field);
    }

    private long asPositiveLong(Object value, String field) {
        long parsed = asEpochSecond(value, field);
        if (parsed <= 0) {
            throw new UnauthorizedException("Campo JWT invalido: " + field);
        }
        return parsed;
    }

    private String asText(Object value, String field) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new UnauthorizedException("Campo JWT ausente: " + field);
    }

    private List<String> asRoleList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String text && StringUtils.hasText(text)) {
                roles.add(text.trim().toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(roles);
    }

    public record AccessToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record JwtPrincipal(
            Long userId,
            String email,
            List<String> roles,
            Instant expiresAt
    ) {
    }
}
