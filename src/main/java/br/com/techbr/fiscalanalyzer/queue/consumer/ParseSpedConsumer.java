package br.com.techbr.fiscalanalyzer.queue.consumer;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.queue.message.ParseSpedMessage;
import br.com.techbr.fiscalanalyzer.queue.util.RabbitHeaderUtils;
import br.com.techbr.fiscalanalyzer.sped.service.ParseSpedService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ParseSpedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseSpedConsumer.class);

    private final ParseSpedService parseSpedService;
    private final Counter retryCounter;

    public ParseSpedConsumer(ParseSpedService parseSpedService, MeterRegistry meterRegistry) {
        this.parseSpedService = parseSpedService;
        this.retryCounter = Counter.builder("queue.retry")
                .tag("queue", "parseSpedQueue")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "${app.queue.parse-sped}", containerFactory = "parseSpedRabbitListenerContainerFactory")
    public void handle(ParseSpedMessage message,
                       @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                       @Header(name = "x-death", required = false) Object xDeath) {
        String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
        int retryCount = RabbitHeaderUtils.retryCountFromXDeath(xDeath);
        if (retryCount > 0) {
            retryCounter.increment();
        }

        try {
            parseSpedService.process(message, corr);
        } catch (ValidationException ex) {
            log.warn("sped.parse.validation_ack spedFileId={} correlationId={} retryCount={} message={}",
                    message.spedFileId(), corr, retryCount, ex.getMessage());
        } catch (InfraException ex) {
            log.error("sped.parse.retry spedFileId={} correlationId={} retryCount={} message={}",
                    message.spedFileId(), corr, retryCount, ex.getMessage(), ex);
            throw ex;
        }
    }
}

