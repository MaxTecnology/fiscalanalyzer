package br.com.techbr.fiscalanalyzer.agent.repository;

import br.com.techbr.fiscalanalyzer.agent.model.AgentAuthAudit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AgentAuthAuditRepository extends JpaRepository<AgentAuthAudit, Long> {

    @Modifying
    @Query(value = """
            delete from agent_auth_audit
            where id in (
                select id
                from agent_auth_audit
                where created_at < :cutoff
                order by created_at
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
