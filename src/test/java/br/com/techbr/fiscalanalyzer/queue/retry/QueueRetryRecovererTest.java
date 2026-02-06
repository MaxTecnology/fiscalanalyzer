package br.com.techbr.fiscalanalyzer.queue.retry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueRetryRecovererTest {

    @Test
    void recover_incrementsDlqCounter_andRejects() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QueueRetryRecoverer recoverer = new QueueRetryRecoverer(
                registry,
                new ObjectMapper(),
                "import.extract"
        );

        MessageProperties props = new MessageProperties();
        props.setCorrelationId("corr-1");
        props.setHeader("x-death", List.of(Map.of("count", 5L)));
        String body = "{\"importacaoId\":1,\"importItemId\":2}";
        Message message = new Message(body.getBytes(StandardCharsets.UTF_8), props);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> recoverer.recover(message, new RuntimeException("fail")));
        assertEquals(1.0, registry.counter("queue.dlq", "queue", "import.extract").count());
    }
}
