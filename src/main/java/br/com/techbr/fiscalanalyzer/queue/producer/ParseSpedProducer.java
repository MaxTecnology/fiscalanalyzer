package br.com.techbr.fiscalanalyzer.queue.producer;

import br.com.techbr.fiscalanalyzer.queue.message.ParseSpedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ParseSpedProducer {

    private static final Logger log = LoggerFactory.getLogger(ParseSpedProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public ParseSpedProducer(RabbitTemplate rabbitTemplate,
                             @Value("${app.queue.exchange}") String exchange,
                             @Value("${app.queue.parse-sped}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void send(ParseSpedMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("queue.parse_sped.sent spedFileId={} correlationId={} objectKey={} entry={}",
                message.spedFileId(),
                message.correlationId(),
                message.objectKey(),
                message.zipEntryName());
    }
}

