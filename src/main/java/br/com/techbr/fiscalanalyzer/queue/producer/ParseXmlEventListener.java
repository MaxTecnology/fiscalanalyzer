package br.com.techbr.fiscalanalyzer.queue.producer;

import br.com.techbr.fiscalanalyzer.importacao.event.ParseXmlRequestedEvent;
import br.com.techbr.fiscalanalyzer.queue.message.ParseXmlMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ParseXmlEventListener {

    private final ParseXmlProducer parseXmlProducer;

    public ParseXmlEventListener(ParseXmlProducer parseXmlProducer) {
        this.parseXmlProducer = parseXmlProducer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onParseXmlRequested(ParseXmlRequestedEvent event) {
        parseXmlProducer.send(new ParseXmlMessage(
                event.importacaoId(),
                event.importItemId(),
                event.bucket(),
                event.objectKeyZip(),
                event.zipEntryName(),
                event.sha256()
        ), event.correlationId());
    }
}
