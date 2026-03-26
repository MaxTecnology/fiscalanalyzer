package br.com.techbr.fiscalanalyzer.agent.repository;

import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatus;
import br.com.techbr.fiscalanalyzer.agent.model.AgentInstanceStatusType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentInstanceStatusRepository extends JpaRepository<AgentInstanceStatus, Long> {

    Optional<AgentInstanceStatus> findByTenantIdAndEmpresaIdAndAgentId(Long tenantId, Long empresaId, String agentId);

    List<AgentInstanceStatus> findByTenantIdAndEmpresaIdOrderByLastSeenAtDesc(Long tenantId, Long empresaId);

    Page<AgentInstanceStatus> findByTenantIdAndEmpresaIdOrderByLastSeenAtDesc(Long tenantId, Long empresaId, Pageable pageable);

    List<AgentInstanceStatus> findByTenantIdAndEmpresaIdAndStatusOrderByLastSeenAtDesc(
            Long tenantId, Long empresaId, AgentInstanceStatusType status
    );

    Page<AgentInstanceStatus> findByTenantIdAndEmpresaIdAndStatusOrderByLastSeenAtDesc(
            Long tenantId, Long empresaId, AgentInstanceStatusType status, Pageable pageable
    );

    @Modifying
    @Query("""
            update AgentInstanceStatus s
               set s.status = :status,
                   s.updatedAt = :now
             where s.status in :currentStatuses
               and s.lastSeenAt < :cutoff
            """)
    int markStatusByLastSeenBefore(@Param("status") AgentInstanceStatusType status,
                                   @Param("currentStatuses") Collection<AgentInstanceStatusType> currentStatuses,
                                   @Param("cutoff") Instant cutoff,
                                   @Param("now") Instant now);
}
