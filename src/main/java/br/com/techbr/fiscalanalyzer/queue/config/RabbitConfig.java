package br.com.techbr.fiscalanalyzer.queue.config;

import br.com.techbr.fiscalanalyzer.queue.retry.QueueRetryRecoverer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange importExchange(@Value("${app.queue.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(@Value("${app.queue.dlx}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue extractZipQueue(@Value("${app.queue.extract-zip}") String queueName,
                                 @Value("${app.queue.dlx}") String dlx,
                                 @Value("${app.queue.extract-dlq}") String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue parseXmlQueue(@Value("${app.queue.parse-xml}") String queueName,
                               @Value("${app.queue.dlx}") String dlx,
                               @Value("${app.queue.parse-dlq}") String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue extractZipDlq(@Value("${app.queue.extract-dlq}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue parseXmlDlq(@Value("${app.queue.parse-dlq}") String queueName) {
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
    public Binding extractZipDlqBinding(Queue extractZipDlq,
                                        DirectExchange deadLetterExchange,
                                        @Value("${app.queue.extract-dlq}") String routingKey) {
        return BindingBuilder.bind(extractZipDlq).to(deadLetterExchange).with(routingKey);
    }

    @Bean
    public Binding parseXmlDlqBinding(Queue parseXmlDlq,
                                      DirectExchange deadLetterExchange,
                                      @Value("${app.queue.parse-dlq}") String routingKey) {
        return BindingBuilder.bind(parseXmlDlq).to(deadLetterExchange).with(routingKey);
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

    @Bean
    public MethodInterceptor extractRetryInterceptor(
            QueueRetryRecoverer extractZipRecoverer,
            @Value("${app.queue.retry.max-attempts:5}") int maxAttempts,
            @Value("${app.queue.retry.initial-interval:1000}") long initialInterval,
            @Value("${app.queue.retry.multiplier:2.0}") double multiplier,
            @Value("${app.queue.retry.max-interval:30000}") long maxInterval
    ) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(Math.max(0, maxAttempts - 1))
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(extractZipRecoverer)
                .build();
    }

    @Bean
    public MethodInterceptor parseRetryInterceptor(
            QueueRetryRecoverer parseXmlRecoverer,
            @Value("${app.queue.retry.max-attempts:5}") int maxAttempts,
            @Value("${app.queue.retry.initial-interval:1000}") long initialInterval,
            @Value("${app.queue.retry.multiplier:2.0}") double multiplier,
            @Value("${app.queue.retry.max-interval:30000}") long maxInterval
    ) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(Math.max(0, maxAttempts - 1))
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(parseXmlRecoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory extractRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MethodInterceptor extractRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(extractRetryInterceptor);
        return factory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory parseRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MethodInterceptor parseRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(parseRetryInterceptor);
        return factory;
    }

    @Bean
    public QueueRetryRecoverer extractZipRecoverer(
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            @Value("${app.queue.extract-zip}") String queueName
    ) {
        return new QueueRetryRecoverer(meterRegistry, objectMapper, queueName);
    }

    @Bean
    public QueueRetryRecoverer parseXmlRecoverer(
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            @Value("${app.queue.parse-xml}") String queueName
    ) {
        return new QueueRetryRecoverer(meterRegistry, objectMapper, queueName);
    }
}
