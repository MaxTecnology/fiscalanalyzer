package br.com.techbr.fiscalanalyzer.queue.producer;

import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ParseXmlProducer {

    private static final Logger log = LoggerFactory.getLogger(ParseXmlProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final String exchangeName;

    public ParseXmlProducer(RabbitTemplate rabbitTemplate,
                            @Value("${app.queue.parse-xml}") String queueName,
                            @Value("${app.queue.exchange}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.exchangeName = exchangeName;
    }

    public void send(ParseXmlMessage message, String correlationId) {
        rabbitTemplate.convertAndSend(exchangeName, queueName, message, m -> {
            m.getMessageProperties().setCorrelationId(correlationId);
            return m;
        });
        log.info("queue.parse.sent importacaoId={} importItemId={} correlationId={} entry={}",
                message.importacaoId(), message.importItemId(), correlationId, message.zipEntryName());
    }
}
