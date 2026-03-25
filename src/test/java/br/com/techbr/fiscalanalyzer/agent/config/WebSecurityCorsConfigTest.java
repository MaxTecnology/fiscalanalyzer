package br.com.techbr.fiscalanalyzer.agent.config;

import br.com.techbr.fiscalanalyzer.agent.security.ApiSecurityInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebSecurityCorsConfigTest {

    @Test
    void addCorsMappings_configuraOrigenseMetodos() {
        WebSecurityConfig config = new WebSecurityConfig(
                mock(ApiSecurityInterceptor.class),
                "https://front.example.com,https://admin.example.com"
        );
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        config.addCorsMappings(registry);

        Map<String, CorsConfiguration> corsConfigurations = registry.expose();
        CorsConfiguration cors = corsConfigurations.get("/**");

        assertNotNull(cors);
        assertEquals(
                List.of("https://front.example.com", "https://admin.example.com"),
                cors.getAllowedOriginPatterns()
        );
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertEquals(List.of("*"), cors.getAllowedHeaders());
        assertEquals(List.of("Retry-After"), cors.getExposedHeaders());
        assertEquals(Boolean.FALSE, cors.getAllowCredentials());
    }

    private static final class ExposedCorsRegistry extends CorsRegistry {
        public Map<String, CorsConfiguration> expose() {
            return super.getCorsConfigurations();
        }
    }
}
