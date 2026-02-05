package br.com.techbr.fiscalanalyzer.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange importExchange(@Value("${app.queue.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue extractZipQueue(@Value("${app.queue.extract-zip}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue parseXmlQueue(@Value("${app.queue.parse-xml}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding extractZipBinding(Queue extractZipQueue,
                                     DirectExchange importExchange,
                                     @Value("${app.queue.extract-zip}") String routingKey) {
        return BindingBuilder.bind(extractZipQueue).to(importExchange).with(routingKey);
    }

    @Bean
    public Binding parseXmlBinding(Queue parseXmlQueue,
                                   DirectExchange importExchange,
                                   @Value("${app.queue.parse-xml}") String routingKey) {
        return BindingBuilder.bind(parseXmlQueue).to(importExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
