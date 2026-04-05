package br.com.techbr.fiscalanalyzer.sped.event;

import br.com.techbr.fiscalanalyzer.queue.message.ParseSpedMessage;
import br.com.techbr.fiscalanalyzer.queue.producer.ParseSpedProducer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ParseSpedRequestedEventListener {

    private final ParseSpedProducer parseSpedProducer;

    public ParseSpedRequestedEventListener(ParseSpedProducer parseSpedProducer) {
        this.parseSpedProducer = parseSpedProducer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(ParseSpedRequestedEvent event) {
        parseSpedProducer.send(new ParseSpedMessage(
                event.spedFileId(),
                event.bucket(),
                event.objectKey(),
                event.zipEntryName(),
                event.sha256(),
                event.correlationId()
        ));
    }
}

