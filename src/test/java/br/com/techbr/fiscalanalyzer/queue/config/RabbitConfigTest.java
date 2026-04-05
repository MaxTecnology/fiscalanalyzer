package br.com.techbr.fiscalanalyzer.queue.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RabbitConfigTest {

    @Test
    void queuesHaveDlqArguments() {
        RabbitConfig config = new RabbitConfig();
        Queue extract = config.extractZipQueue("import.extract", "import.dlx", "import.extract.dlq");
        Queue parse = config.parseXmlQueue("import.parse", "import.dlx", "import.parse.dlq");
        Queue parseSped = config.parseSpedQueue("import.sped.parse", "import.dlx", "import.sped.parse.dlq");

        assertEquals("import.dlx", extract.getArguments().get("x-dead-letter-exchange"));
        assertEquals("import.extract.dlq", extract.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("import.dlx", parse.getArguments().get("x-dead-letter-exchange"));
        assertEquals("import.parse.dlq", parse.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("import.dlx", parseSped.getArguments().get("x-dead-letter-exchange"));
        assertEquals("import.sped.parse.dlq", parseSped.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void listenerFactoriesApplyConcurrencyAndPrefetch() {
        RabbitConfig config = new RabbitConfig();
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        MessageConverter converter = mock(MessageConverter.class);
        MethodInterceptor interceptor = mock(MethodInterceptor.class);

        SimpleRabbitListenerContainerFactory extractFactory = config.extractRabbitListenerContainerFactory(
                connectionFactory,
                converter,
                interceptor,
                2,
                4,
                20
        );

        SimpleRabbitListenerContainerFactory parseFactory = config.parseRabbitListenerContainerFactory(
                connectionFactory,
                converter,
                interceptor,
                3,
                6,
                30
        );

        SimpleRabbitListenerContainerFactory parseSpedFactory = config.parseSpedRabbitListenerContainerFactory(
                connectionFactory,
                converter,
                interceptor,
                1,
                2,
                11
        );

        assertEquals(2, ReflectionTestUtils.getField(extractFactory, "concurrentConsumers"));
        assertEquals(4, ReflectionTestUtils.getField(extractFactory, "maxConcurrentConsumers"));
        assertEquals(20, ReflectionTestUtils.getField(extractFactory, "prefetchCount"));

        assertEquals(3, ReflectionTestUtils.getField(parseFactory, "concurrentConsumers"));
        assertEquals(6, ReflectionTestUtils.getField(parseFactory, "maxConcurrentConsumers"));
        assertEquals(30, ReflectionTestUtils.getField(parseFactory, "prefetchCount"));

        assertEquals(1, ReflectionTestUtils.getField(parseSpedFactory, "concurrentConsumers"));
        assertEquals(2, ReflectionTestUtils.getField(parseSpedFactory, "maxConcurrentConsumers"));
        assertEquals(11, ReflectionTestUtils.getField(parseSpedFactory, "prefetchCount"));
    }
}
