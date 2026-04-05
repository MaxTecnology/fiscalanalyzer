package br.com.techbr.fiscalanalyzer.queue.consumer;

import br.com.techbr.fiscalanalyzer.queue.message.ParseSpedMessage;
import br.com.techbr.fiscalanalyzer.queue.util.RabbitHeaderUtils;
import br.com.techbr.fiscalanalyzer.sped.service.SpedDlqHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ParseSpedDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseSpedDlqConsumer.class);

    private final SpedDlqHandlerService spedDlqHandlerService;

    public ParseSpedDlqConsumer(SpedDlqHandlerService spedDlqHandlerService) {
        this.spedDlqHandlerService = spedDlqHandlerService;
    }

    @RabbitListener(queues = "${app.queue.parse-sped-dlq}", containerFactory = "dlqRabbitListenerContainerFactory")
    public void handle(ParseSpedMessage message,
                       @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                       @Header(name = "x-death", required = false) Object xDeath) {
        String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
        int retryCount = RabbitHeaderUtils.retryCountFromXDeath(xDeath);
        log.error("dlq.sped.received spedFileId={} correlationId={} retryCount={}",
                message.spedFileId(), corr, retryCount);

        try {
            spedDlqHandlerService.markFileFalhaPermanente(
                    message.spedFileId(),
                    "parse_sped_retry_exhausted"
            );
        } catch (Exception ex) {
            log.error("dlq.sped.handler_error spedFileId={} cause={}",
                    message.spedFileId(), ex.getMessage(), ex);
        }
    }
}

