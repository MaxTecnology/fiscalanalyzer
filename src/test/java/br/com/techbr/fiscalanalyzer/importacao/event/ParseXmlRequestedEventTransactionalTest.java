package br.com.techbr.fiscalanalyzer.importacao.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ParseXmlRequestedEventTransactionalTest.TestConfig.class)
class ParseXmlRequestedEventTransactionalTest {

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        TestEventService testEventService(ApplicationEventPublisher publisher) {
            return new TestEventService(publisher);
        }

        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    static class TestEventService {
        private final ApplicationEventPublisher publisher;

        TestEventService(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishSuccess() {
            publisher.publishEvent(sampleEvent("corr-ok"));
        }

        @Transactional
        public void publishAndRollback() {
            publisher.publishEvent(sampleEvent("corr-rollback"));
            throw new RuntimeException("rollback");
        }

        private ParseXmlRequestedEvent sampleEvent(String correlationId) {
            return new ParseXmlRequestedEvent(
                    1L,
                    10L,
                    "bucket",
                    "object.zip",
                    "file.xml",
                    "sha",
                    correlationId
            );
        }
    }

    static class TestEventListener {
        private final List<ParseXmlRequestedEvent> events = new ArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
        public void onEvent(ParseXmlRequestedEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        int size() {
            return events.size();
        }
    }

    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
            // no-op
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
            // no-op
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
            // no-op
        }
    }

    @Autowired
    private TestEventService service;

    @Autowired
    private TestEventListener listener;

    @Test
    void eventFiresAfterCommit() {
        listener.clear();
        service.publishSuccess();
        assertEquals(1, listener.size());
    }

    @Test
    void eventNotFiredOnRollback() {
        listener.clear();
        assertThrows(RuntimeException.class, () -> service.publishAndRollback());
        assertEquals(0, listener.size());
    }
}
