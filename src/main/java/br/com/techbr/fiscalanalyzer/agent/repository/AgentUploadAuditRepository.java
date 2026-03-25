package br.com.techbr.fiscalanalyzer.agent.repository;

import br.com.techbr.fiscalanalyzer.agent.model.AgentUploadAudit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AgentUploadAuditRepository extends JpaRepository<AgentUploadAudit, Long> {

    @Modifying
    @Query(value = """
            delete from agent_upload_audit
            where id in (
                select id
                from agent_upload_audit
                where created_at < :cutoff
                order by created_at
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
