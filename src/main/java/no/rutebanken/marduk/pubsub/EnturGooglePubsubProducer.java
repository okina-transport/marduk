package no.rutebanken.marduk.pubsub;

import org.apache.camel.Exchange;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubProducer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static no.rutebanken.marduk.pubsub.EnturGooglePubsubConstants.GOOGLE_PUB_SUB_HEADER_PREFIX;
import static no.rutebanken.marduk.pubsub.EnturGooglePubsubConstants.GOOGLE_PUB_SUB_MAX_ATTR_LENGTH;

public class EnturGooglePubsubProducer extends GooglePubsubProducer {


    public EnturGooglePubsubProducer(GooglePubsubEndpoint endpoint) throws Exception {
        super(endpoint);
    }


    @Override
    public void process(Exchange exchange) throws Exception {
        wrapHeaders(exchange);
        super.process(exchange);
    }


    protected void wrapHeaders(Exchange e) {
        Map<String, String> pubSubAttributes = e.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, new HashMap<>(), Map.class);
// TODO log discarded?
        e.getIn().getHeaders().entrySet().stream()
                .filter(entry -> !entry.getKey().toString().startsWith(GOOGLE_PUB_SUB_HEADER_PREFIX))
                .filter(entry -> Objects.toString(entry.getValue(), "").length() <= GOOGLE_PUB_SUB_MAX_ATTR_LENGTH)
                .forEach(entry -> pubSubAttributes.put(entry.getKey(), Objects.toString(entry.getValue())));


        e.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubSubAttributes);
    }
}
