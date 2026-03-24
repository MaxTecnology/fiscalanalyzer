package br.com.techbr.fiscalanalyzer.queue.consumer;

import br.com.techbr.fiscalanalyzer.importacao.service.DlqHandlerService;
import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import br.com.techbr.fiscalanalyzer.queue.util.RabbitHeaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consome mensagens da DLQ de extração após esgotamento de retries.
 *
 * <p>A falha de extração afeta a importação inteira (não há import_item ainda criado).
 * Não possui retry — falhas aqui são apenas logadas e a mensagem é ACKada.
 */
@Component
public class ExtractDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExtractDlqConsumer.class);

    private final DlqHandlerService dlqHandlerService;

    public ExtractDlqConsumer(DlqHandlerService dlqHandlerService) {
        this.dlqHandlerService = dlqHandlerService;
    }

    @RabbitListener(queues = "${app.queue.extract-dlq}", containerFactory = "dlqRabbitListenerContainerFactory")
    public void handle(ExtractZipMessage message,
                       @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId,
                       @Header(name = "x-death", required = false) Object xDeath) {
        int retryCount = RabbitHeaderUtils.retryCountFromXDeath(xDeath);
        log.error("dlq.extract.received importacaoId={} correlationId={} retryCount={}",
                message.importacaoId(), correlationId, retryCount);
        try {
            dlqHandlerService.markImportacaoFalha(
                    message.importacaoId(),
                    "Esgotadas " + retryCount + " tentativas de extração do ZIP"
            );
        } catch (Exception ex) {
            log.error("dlq.extract.handler_error importacaoId={} cause={}",
                    message.importacaoId(), ex.getMessage(), ex);
        }
    }
}
