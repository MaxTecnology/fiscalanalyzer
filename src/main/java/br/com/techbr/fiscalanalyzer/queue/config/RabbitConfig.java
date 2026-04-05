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
    public Queue parseSpedQueue(@Value("${app.queue.parse-sped}") String queueName,
                                @Value("${app.queue.dlx}") String dlx,
                                @Value("${app.queue.parse-sped-dlq}") String dlqRoutingKey) {
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
    public Queue parseSpedDlq(@Value("${app.queue.parse-sped-dlq}") String queueName) {
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
    public Binding parseSpedBinding(Queue parseSpedQueue,
                                    DirectExchange importExchange,
                                    @Value("${app.queue.parse-sped}") String routingKey) {
        return BindingBuilder.bind(parseSpedQueue).to(importExchange).with(routingKey);
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
    public Binding parseSpedDlqBinding(Queue parseSpedDlq,
                                       DirectExchange deadLetterExchange,
                                       @Value("${app.queue.parse-sped-dlq}") String routingKey) {
        return BindingBuilder.bind(parseSpedDlq).to(deadLetterExchange).with(routingKey);
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
    public MethodInterceptor parseSpedRetryInterceptor(
            QueueRetryRecoverer parseSpedRecoverer,
            @Value("${app.queue.retry.max-attempts:5}") int maxAttempts,
            @Value("${app.queue.retry.initial-interval:1000}") long initialInterval,
            @Value("${app.queue.retry.multiplier:2.0}") double multiplier,
            @Value("${app.queue.retry.max-interval:30000}") long maxInterval
    ) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(Math.max(0, maxAttempts - 1))
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(parseSpedRecoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory extractRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MethodInterceptor extractRetryInterceptor,
            @Value("${app.queue.listener.extract.concurrency:1}") int concurrency,
            @Value("${app.queue.listener.extract.max-concurrency:2}") int maxConcurrency,
            @Value("${app.queue.listener.extract.prefetch:10}") int prefetch
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(extractRetryInterceptor);
        factory.setConcurrentConsumers(Math.max(1, concurrency));
        factory.setMaxConcurrentConsumers(Math.max(Math.max(1, concurrency), maxConcurrency));
        factory.setPrefetchCount(Math.max(1, prefetch));
        return factory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory parseRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MethodInterceptor parseRetryInterceptor,
            @Value("${app.queue.listener.parse.concurrency:2}") int concurrency,
            @Value("${app.queue.listener.parse.max-concurrency:8}") int maxConcurrency,
            @Value("${app.queue.listener.parse.prefetch:50}") int prefetch
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(parseRetryInterceptor);
        factory.setConcurrentConsumers(Math.max(1, concurrency));
        factory.setMaxConcurrentConsumers(Math.max(Math.max(1, concurrency), maxConcurrency));
        factory.setPrefetchCount(Math.max(1, prefetch));
        return factory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory parseSpedRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MethodInterceptor parseSpedRetryInterceptor,
            @Value("${app.queue.listener.sped.concurrency:1}") int concurrency,
            @Value("${app.queue.listener.sped.max-concurrency:2}") int maxConcurrency,
            @Value("${app.queue.listener.sped.prefetch:10}") int prefetch
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(parseSpedRetryInterceptor);
        factory.setConcurrentConsumers(Math.max(1, concurrency));
        factory.setMaxConcurrentConsumers(Math.max(Math.max(1, concurrency), maxConcurrency));
        factory.setPrefetchCount(Math.max(1, prefetch));
        return factory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory dlqRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        // Sem retry: falhas nos DLQ consumers são logadas e ACKadas internamente
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

    @Bean
    public QueueRetryRecoverer parseSpedRecoverer(
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            @Value("${app.queue.parse-sped}") String queueName
    ) {
        return new QueueRetryRecoverer(meterRegistry, objectMapper, queueName);
    }
}
