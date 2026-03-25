package br.com.techbr.fiscalanalyzer.identity.security;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void issueAndValidate_roundTrip_ok() {
        JwtTokenService service = new JwtTokenService(new ObjectMapper().findAndRegisterModules(), SECRET, 3600);

        JwtTokenService.AccessToken token = service.issueAccessToken(10L, "admin@empresa.com", List.of("ADMIN"));
        JwtTokenService.JwtPrincipal principal = service.parseAndValidateAccessToken(token.token());

        assertEquals(10L, principal.userId());
        assertEquals("admin@empresa.com", principal.email());
        assertEquals(List.of("ADMIN"), principal.roles());
    }

    @Test
    void validate_quandoAssinaturaInvalida_lancaUnauthorized() {
        JwtTokenService service = new JwtTokenService(new ObjectMapper().findAndRegisterModules(), SECRET, 3600);
        JwtTokenService.AccessToken token = service.issueAccessToken(10L, "admin@empresa.com", List.of("ADMIN"));
        String tampered = token.token().substring(0, token.token().length() - 2) + "aa";

        assertThrows(UnauthorizedException.class, () -> service.parseAndValidateAccessToken(tampered));
    }

    @Test
    void validate_quandoExpirado_lancaUnauthorized() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JwtTokenService service = new JwtTokenService(mapper, SECRET, 3600);
        String expiredToken = buildToken(mapper, SECRET, Instant.now().minusSeconds(10).getEpochSecond());

        assertThrows(UnauthorizedException.class, () -> service.parseAndValidateAccessToken(expiredToken));
    }

    @Test
    void issue_quandoSecretInvalido_lancaInfraException() {
        JwtTokenService service = new JwtTokenService(new ObjectMapper().findAndRegisterModules(), "short-secret", 3600);

        assertThrows(InfraException.class, () -> service.issueAccessToken(1L, "user@empresa.com", List.of("ADMIN")));
    }

    private String buildToken(ObjectMapper mapper, String secret, long expEpochSecond) throws Exception {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "fiscalanalyzer");
        payload.put("aud", "fiscalanalyzer-admin");
        payload.put("sub", "10");
        payload.put("email", "admin@empresa.com");
        payload.put("roles", List.of("ADMIN"));
        payload.put("iat", Instant.now().minusSeconds(30).getEpochSecond());
        payload.put("exp", expEpochSecond);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(mapper.writeValueAsBytes(header));
        String encodedPayload = encoder.encodeToString(mapper.writeValueAsBytes(payload));
        String signingInput = encodedHeader + "." + encodedPayload;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }
}

