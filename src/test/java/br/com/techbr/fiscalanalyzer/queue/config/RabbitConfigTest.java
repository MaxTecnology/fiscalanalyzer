package br.com.techbr.fiscalanalyzer.queue.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitConfigTest {

    @Test
    void queuesHaveDlqArguments() {
        RabbitConfig config = new RabbitConfig();
        Queue extract = config.extractZipQueue("import.extract", "import.dlx", "import.extract.dlq");
        Queue parse = config.parseXmlQueue("import.parse", "import.dlx", "import.parse.dlq");

        assertEquals("import.dlx", extract.getArguments().get("x-dead-letter-exchange"));
        assertEquals("import.extract.dlq", extract.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("import.dlx", parse.getArguments().get("x-dead-letter-exchange"));
        assertEquals("import.parse.dlq", parse.getArguments().get("x-dead-letter-routing-key"));
    }
}
