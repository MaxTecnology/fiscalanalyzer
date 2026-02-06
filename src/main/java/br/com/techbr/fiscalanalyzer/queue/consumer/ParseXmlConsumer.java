package br.com.techbr.fiscalanalyzer.queue.consumer;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.service.ParseXmlService;
import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import br.com.techbr.fiscalanalyzer.queue.util.RabbitHeaderUtils;
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
public class ParseXmlConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseXmlConsumer.class);

    private final ParseXmlService parseXmlService;
    private final Counter retryCounter;

    public ParseXmlConsumer(ParseXmlService parseXmlService, MeterRegistry meterRegistry) {
        this.parseXmlService = parseXmlService;
        this.retryCounter = Counter.builder("queue.retry")
                .tag("queue", "parseXmlQueue")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "${app.queue.parse-xml}", containerFactory = "parseRabbitListenerContainerFactory")
    public void handle(ParseXmlMessage message,
                       @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                       @Header(name = "x-death", required = false) Object xDeath) {
        String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
        int retryCount = RabbitHeaderUtils.retryCountFromXDeath(xDeath);
        if (retryCount > 0) {
            retryCounter.increment();
        }
        try {
            parseXmlService.process(message, corr);
        } catch (ValidationException ex) {
            log.warn("import.parse.validation importacaoId={} importItemId={} correlationId={} retryCount={} message={}",
                    message.importacaoId(), message.importItemId(), corr, retryCount, ex.getMessage());
        } catch (InfraException ex) {
            log.error("import.parse.retry importacaoId={} importItemId={} correlationId={} retryCount={} message={}",
                    message.importacaoId(), message.importItemId(), corr, retryCount, ex.getMessage(), ex);
            throw ex;
        }
    }
}
