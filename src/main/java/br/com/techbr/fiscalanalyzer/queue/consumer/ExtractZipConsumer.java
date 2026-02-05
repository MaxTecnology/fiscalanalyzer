package br.com.techbr.fiscalanalyzer.queue.consumer;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.importacao.service.ExtractZipService;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExtractZipConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExtractZipConsumer.class);

    private final ExtractZipService extractZipService;

    public ExtractZipConsumer(ExtractZipService extractZipService) {
        this.extractZipService = extractZipService;
    }

    @RabbitListener(queues = "${app.queue.extract-zip}")
    public void handle(ExtractZipMessage message,
                       @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
        String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
        try {
            extractZipService.process(message, corr);
        } catch (ValidationException ex) {
            log.warn("import.extract.validation importacaoId={} correlationId={} message={}",
                    message.importacaoId(), corr, ex.getMessage());
        } catch (InfraException ex) {
            log.error("import.extract.retry importacaoId={} correlationId={} message={}",
                    message.importacaoId(), corr, ex.getMessage(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("import.extract.error importacaoId={} correlationId={} message={}",
                    message.importacaoId(), corr, ex.getMessage(), ex);
            throw ex;
        }
    }
}
