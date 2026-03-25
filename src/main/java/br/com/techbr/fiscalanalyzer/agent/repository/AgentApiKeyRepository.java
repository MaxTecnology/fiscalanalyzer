package br.com.techbr.fiscalanalyzer.agent.repository;

import br.com.techbr.fiscalanalyzer.agent.model.AgentApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentApiKeyRepository extends JpaRepository<AgentApiKey, Long> {

    Optional<AgentApiKey> findByKeyHash(String keyHash);

    List<AgentApiKey> findByTenantIdAndEmpresaIdOrderByCriadoAtDesc(Long tenantId, Long empresaId);

    @Modifying
    @Query("""
            update AgentApiKey k
               set k.ativo = false,
                   k.revogadoAt = :revogadoAt
             where k.id = :keyId
               and k.tenantId = :tenantId
               and k.empresaId = :empresaId
               and k.ativo = true
               and k.revogadoAt is null
            """)
    int revokeIfActive(@Param("tenantId") Long tenantId,
                       @Param("empresaId") Long empresaId,
                       @Param("keyId") Long keyId,
                       @Param("revogadoAt") Instant revogadoAt);
}
