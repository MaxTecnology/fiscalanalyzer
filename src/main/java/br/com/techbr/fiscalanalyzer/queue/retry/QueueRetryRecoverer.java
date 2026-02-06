package br.com.techbr.fiscalanalyzer.queue.retry;

import br.com.techbr.fiscalanalyzer.queue.util.RabbitHeaderUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class QueueRetryRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(QueueRetryRecoverer.class);

    private final ObjectMapper objectMapper;
    private final Counter dlqCounter;
    private final String queueName;

    public QueueRetryRecoverer(MeterRegistry meterRegistry, ObjectMapper objectMapper, String queueName) {
        this.objectMapper = objectMapper;
        this.queueName = queueName;
        this.dlqCounter = Counter.builder("queue.dlq")
                .tag("queue", queueName)
                .register(meterRegistry);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        int retryCount = RabbitHeaderUtils.retryCountFromXDeath(
                message.getMessageProperties().getHeaders().get("x-death")
        );
        String correlationId = message.getMessageProperties().getCorrelationId();
        dlqCounter.increment();

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<?, ?> map = null;
        try {
            map = objectMapper.readValue(body, Map.class);
        } catch (Exception ignored) {
        }
        Object importacaoId = map != null ? map.get("importacaoId") : null;
        Object importItemId = map != null ? map.get("importItemId") : null;

        log.error("queue.dlq routed queue={} correlationId={} retryCount={} importacaoId={} importItemId={} cause={}",
                queueName, correlationId, retryCount, importacaoId, importItemId,
                cause != null ? cause.getClass().getSimpleName() : "unknown");

        throw new AmqpRejectAndDontRequeueException("DLQ routed", cause);
    }
}
