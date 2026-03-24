package br.com.techbr.fiscalanalyzer.queue.consumer;

import br.com.techbr.fiscalanalyzer.importacao.service.DlqHandlerService;
import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import br.com.techbr.fiscalanalyzer.queue.util.RabbitHeaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consome mensagens da DLQ de parse após esgotamento de retries.
 *
 * <p>Não possui retry — falhas aqui são apenas logadas e a mensagem é ACKada
 * para evitar loop infinito na DLQ.
 */
@Component
public class ParseDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseDlqConsumer.class);

    private final DlqHandlerService dlqHandlerService;

    public ParseDlqConsumer(DlqHandlerService dlqHandlerService) {
        this.dlqHandlerService = dlqHandlerService;
    }

    @RabbitListener(queues = "${app.queue.parse-dlq}", containerFactory = "dlqRabbitListenerContainerFactory")
    public void handle(ParseXmlMessage message,
                       @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                       @Header(name = "x-death", required = false) Object xDeath) {
        int retryCount = RabbitHeaderUtils.retryCountFromXDeath(xDeath);
        log.error("dlq.parse.received importacaoId={} importItemId={} correlationId={} retryCount={}",
                message.importacaoId(), message.importItemId(), correlationId, retryCount);
        try {
            dlqHandlerService.markItemFalhaPermanente(
                    message.importacaoId(),
                    message.importItemId(),
                    "Esgotadas " + retryCount + " tentativas de parse"
            );
        } catch (Exception ex) {
            log.error("dlq.parse.handler_error importacaoId={} importItemId={} cause={}",
                    message.importacaoId(), message.importItemId(), ex.getMessage(), ex);
        }
    }
}
