package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.dto.AdminCreateAgentKeyRequest;
import br.com.techbr.fiscalanalyzer.agent.model.AgentApiKey;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentApiKeyRepository;
import br.com.techbr.fiscalanalyzer.common.exception.ForbiddenException;
import br.com.techbr.fiscalanalyzer.common.exception.UnauthorizedException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentApiKeyServiceTest {

    @Mock
    private AgentApiKeyRepository repository;

    @Test
    void createKey_geraChaveHashESalva() {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        when(repository.save(any(AgentApiKey.class))).thenAnswer(invocation -> {
            AgentApiKey entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 10L);
            return entity;
        });

        var response = service.createKey(1L, 2L, new AdminCreateAgentKeyRequest("srv", "admin"));

        assertNotNull(response.apiKey());
        assertTrue(response.apiKey().startsWith("fa_live_"));
        assertEquals(10L, response.id());
        verify(repository).save(any(AgentApiKey.class));
    }

    @Test
    void authenticate_validaHashERetornaContexto() throws Exception {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        String rawKey = "fa_live_abc123";
        String hash = sha256(rawKey);

        AgentApiKey key = new AgentApiKey();
        ReflectionTestUtils.setField(key, "id", 55L);
        key.setTenantId(99L);
        key.setEmpresaId(88L);
        key.setKeyHash(hash);
        key.setKeyPrefix("fa_live_abcd");
        key.setAtivo(true);

        when(repository.findByKeyHash(eq(hash))).thenReturn(Optional.of(key));
        when(repository.save(any(AgentApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var ctx = service.authenticate(rawKey);

        assertEquals(55L, ctx.apiKeyId());
        assertEquals(99L, ctx.tenantId());
        assertEquals(88L, ctx.empresaId());
        verify(repository).findByKeyHash(hash);
        verify(repository, atLeastOnce()).save(any(AgentApiKey.class));
    }

    @Test
    void authenticate_invalida_lancaUnauthorized() {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        when(repository.findByKeyHash(any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.authenticate("fa_live_x"));
    }

    @Test
    void authenticate_revogada_lancaForbidden() throws Exception {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        String rawKey = "fa_live_abc123";
        String hash = sha256(rawKey);

        AgentApiKey key = new AgentApiKey();
        key.setKeyHash(hash);
        key.setTenantId(1L);
        key.setEmpresaId(1L);
        key.setAtivo(false);
        key.setRevogadoAt(Instant.now());

        when(repository.findByKeyHash(eq(hash))).thenReturn(Optional.of(key));

        assertThrows(ForbiddenException.class, () -> service.authenticate(rawKey));
    }

    @Test
    void revokeKey_deOutroTenant_lancaForbidden() {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        AgentApiKey key = new AgentApiKey();
        ReflectionTestUtils.setField(key, "id", 77L);
        key.setTenantId(1L);
        key.setEmpresaId(2L);
        key.setAtivo(true);

        when(repository.findById(77L)).thenReturn(Optional.of(key));

        assertThrows(ForbiddenException.class, () -> service.revokeKey(9L, 2L, 77L));
    }

    @Test
    void revokeKey_sucesso() {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        AgentApiKey key = new AgentApiKey();
        ReflectionTestUtils.setField(key, "id", 77L);
        key.setTenantId(1L);
        key.setEmpresaId(2L);
        key.setAtivo(true);

        when(repository.findById(77L)).thenReturn(Optional.of(key));
        when(repository.revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.revokeKey(1L, 2L, 77L));
        verify(repository).revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class));
    }

    @Test
    void revokeKey_jaRevogada_lancaValidation() {
        AgentApiKeyService service = new AgentApiKeyService(repository);
        AgentApiKey key = new AgentApiKey();
        ReflectionTestUtils.setField(key, "id", 77L);
        key.setTenantId(1L);
        key.setEmpresaId(2L);
        key.setAtivo(true);

        when(repository.findById(77L)).thenReturn(Optional.of(key));
        when(repository.revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class))).thenReturn(0);

        assertThrows(ValidationException.class, () -> service.revokeKey(1L, 2L, 77L));
    }

    @Test
    void rotateKey_semRevogarAntiga_mantemAmbasAtivas() {
        AgentApiKeyService service = new AgentApiKeyService(repository);

        AgentApiKey current = new AgentApiKey();
        ReflectionTestUtils.setField(current, "id", 77L);
        current.setTenantId(1L);
        current.setEmpresaId(2L);
        current.setAtivo(true);

        when(repository.findById(77L)).thenReturn(Optional.of(current));
        when(repository.save(any(AgentApiKey.class))).thenAnswer(invocation -> {
            AgentApiKey entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 88L);
            }
            return entity;
        });

        var response = service.rotateKey(1L, 2L, 77L, false, new AdminCreateAgentKeyRequest("rot", "admin"));

        assertNotNull(response.apiKey());
        assertEquals(88L, response.id());
        assertTrue(current.getAtivo());
        assertNull(current.getRevogadoAt());
    }

    @Test
    void rotateKey_comRevogacao_desativaAntiga() {
        AgentApiKeyService service = new AgentApiKeyService(repository);

        AgentApiKey current = new AgentApiKey();
        ReflectionTestUtils.setField(current, "id", 77L);
        current.setTenantId(1L);
        current.setEmpresaId(2L);
        current.setAtivo(true);

        when(repository.findById(77L)).thenReturn(Optional.of(current));
        when(repository.save(any(AgentApiKey.class))).thenAnswer(invocation -> {
            AgentApiKey entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 89L);
            }
            return entity;
        });
        when(repository.revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class))).thenReturn(1);

        service.rotateKey(1L, 2L, 77L, true, new AdminCreateAgentKeyRequest("rot", "admin"));

        verify(repository).revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class));
        verify(repository).save(any(AgentApiKey.class));
    }

    @Test
    void rotateKey_comRevogacaoParalela_lancaValidation() {
        AgentApiKeyService service = new AgentApiKeyService(repository);

        AgentApiKey current = new AgentApiKey();
        ReflectionTestUtils.setField(current, "id", 77L);
        current.setTenantId(1L);
        current.setEmpresaId(2L);
        current.setAtivo(true);

        when(repository.findById(77L)).thenReturn(Optional.of(current));
        when(repository.save(any(AgentApiKey.class))).thenAnswer(invocation -> {
            AgentApiKey entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 89L);
            }
            return entity;
        });
        when(repository.revokeIfActive(eq(1L), eq(2L), eq(77L), any(Instant.class))).thenReturn(0);

        assertThrows(ValidationException.class,
                () -> service.rotateKey(1L, 2L, 77L, true, new AdminCreateAgentKeyRequest("rot", "admin")));
    }

    private String sha256(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(value.getBytes()));
    }
}
