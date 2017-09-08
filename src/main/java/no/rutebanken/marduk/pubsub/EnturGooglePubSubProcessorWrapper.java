package no.rutebanken.marduk.pubsub;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.pubsub.EnturGooglePubsubConstants.GOOGLE_PUB_SUB_HEADER_PREFIX;

public class EnturGooglePubSubProcessorWrapper implements Processor {


    private Processor _processor;


    public EnturGooglePubSubProcessorWrapper(Processor _processor) {
        this._processor = _processor;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        delayRedeliveryIfFailure(exchange);
        unwrapHeaders(exchange);
        _processor.process(exchange);
    }

    private void delayRedeliveryIfFailure(Exchange exchange) {
        exchange.getIn().setHeader(GooglePubsubConstants.ACK_DEADLINE, 30);
    }


    protected void unwrapHeaders(Exchange e) {
        Map<String, String> pubSubAttributes = e.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, new HashMap<>(), Map.class);
        pubSubAttributes.entrySet().stream().filter(entry -> !entry.getKey().toString().startsWith(GOOGLE_PUB_SUB_HEADER_PREFIX)).forEach(entry -> e.getIn().setHeader(entry.getKey(), entry.getValue()));
    }

}
