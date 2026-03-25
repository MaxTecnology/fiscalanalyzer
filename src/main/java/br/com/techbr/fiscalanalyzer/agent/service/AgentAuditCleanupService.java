package br.com.techbr.fiscalanalyzer.agent.service;

import br.com.techbr.fiscalanalyzer.agent.repository.AgentAuthAuditRepository;
import br.com.techbr.fiscalanalyzer.agent.repository.AgentUploadAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AgentAuditCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditCleanupService.class);

    private final AgentAuthAuditRepository authAuditRepository;
    private final AgentUploadAuditRepository uploadAuditRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final boolean enabled;
    private final int authRetentionDays;
    private final int uploadRetentionDays;
    private final int batchSize;
    private final int maxBatches;

    @Autowired
    public AgentAuditCleanupService(AgentAuthAuditRepository authAuditRepository,
                                    AgentUploadAuditRepository uploadAuditRepository,
                                    MeterRegistry meterRegistry,
                                    @Value("${app.security.audit-cleanup.enabled:true}") boolean enabled,
                                    @Value("${app.security.audit-cleanup.auth-retention-days:90}") int authRetentionDays,
                                    @Value("${app.security.audit-cleanup.upload-retention-days:30}") int uploadRetentionDays,
                                    @Value("${app.security.audit-cleanup.batch-size:5000}") int batchSize,
                                    @Value("${app.security.audit-cleanup.max-batches:100}") int maxBatches) {
        this(authAuditRepository, uploadAuditRepository, meterRegistry, Clock.systemUTC(),
                enabled, authRetentionDays, uploadRetentionDays, batchSize, maxBatches);
    }

    AgentAuditCleanupService(AgentAuthAuditRepository authAuditRepository,
                             AgentUploadAuditRepository uploadAuditRepository,
                             MeterRegistry meterRegistry,
                             Clock clock,
                             boolean enabled,
                             int authRetentionDays,
                             int uploadRetentionDays,
                             int batchSize,
                             int maxBatches) {
        this.authAuditRepository = authAuditRepository;
        this.uploadAuditRepository = uploadAuditRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.enabled = enabled;
        this.authRetentionDays = Math.max(1, authRetentionDays);
        this.uploadRetentionDays = Math.max(1, uploadRetentionDays);
        this.batchSize = Math.max(100, batchSize);
        this.maxBatches = Math.max(1, maxBatches);
    }

    @Scheduled(cron = "${app.security.audit-cleanup.cron:0 30 3 * * *}")
    @Transactional
    public void scheduledCleanup() {
        cleanup("scheduled");
    }

    @Transactional
    public CleanupResult cleanup(String trigger) {
        if (!enabled) {
            log.debug("security.audit.cleanup.skipped trigger={} reason=disabled", safeTrigger(trigger));
            return new CleanupResult(0, 0, true);
        }

        Instant now = clock.instant();
        Instant authCutoff = now.minus(authRetentionDays, ChronoUnit.DAYS);
        Instant uploadCutoff = now.minus(uploadRetentionDays, ChronoUnit.DAYS);

        long authDeleted = deleteInBatches(authCutoff, true);
        long uploadDeleted = deleteInBatches(uploadCutoff, false);

        if (authDeleted > 0) {
            meterRegistry.counter("security.audit.cleanup.deleted", "table", "agent_auth_audit").increment(authDeleted);
        }
        if (uploadDeleted > 0) {
            meterRegistry.counter("security.audit.cleanup.deleted", "table", "agent_upload_audit").increment(uploadDeleted);
        }

        log.info("security.audit.cleanup.completed trigger={} authDeleted={} uploadDeleted={} authRetentionDays={} uploadRetentionDays={} batchSize={} maxBatches={}",
                safeTrigger(trigger), authDeleted, uploadDeleted, authRetentionDays, uploadRetentionDays, batchSize, maxBatches);

        return new CleanupResult(authDeleted, uploadDeleted, false);
    }

    private long deleteInBatches(Instant cutoff, boolean authTable) {
        long totalDeleted = 0;

        for (int i = 0; i < maxBatches; i++) {
            int deleted = authTable
                    ? authAuditRepository.deleteBatchBefore(cutoff, batchSize)
                    : uploadAuditRepository.deleteBatchBefore(cutoff, batchSize);

            if (deleted <= 0) {
                break;
            }

            totalDeleted += deleted;

            if (deleted < batchSize) {
                break;
            }
        }

        return totalDeleted;
    }

    private String safeTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return "unspecified";
        }
        return trigger;
    }

    public record CleanupResult(long authDeleted, long uploadDeleted, boolean skipped) {
    }
}
