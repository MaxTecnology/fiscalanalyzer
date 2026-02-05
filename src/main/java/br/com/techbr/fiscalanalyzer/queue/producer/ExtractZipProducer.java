package br.com.techbr.fiscalanalyzer.queue.producer;

import br.com.techbr.fiscalanalyzer.queue.message.ExtractZipMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExtractZipProducer {

    private static final Logger log = LoggerFactory.getLogger(ExtractZipProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final String exchangeName;

    public ExtractZipProducer(RabbitTemplate rabbitTemplate,
                              @Value("${app.queue.extract-zip}") String queueName,
                              @Value("${app.queue.exchange}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.exchangeName = exchangeName;
    }

    public void send(ExtractZipMessage message) {
        String correlationId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend(exchangeName, queueName, message, m -> {
            m.getMessageProperties().setCorrelationId(correlationId);
            return m;
        });
        log.info("queue.extract.sent importacaoId={} correlationId={} bucket={} key={} sha256={}",
                message.importacaoId(), correlationId, message.bucket(), message.objectKey(), message.sha256());
    }
}
