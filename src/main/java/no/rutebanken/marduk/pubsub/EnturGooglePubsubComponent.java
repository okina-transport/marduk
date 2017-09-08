package no.rutebanken.marduk.pubsub;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;

import java.util.Map;

public class EnturGooglePubsubComponent extends GooglePubsubComponent {


    public EnturGooglePubsubComponent() {
        super();
        setEndpointClass(EnturGooglePubsubEndpoint.class);
    }

    public EnturGooglePubsubComponent(CamelContext context) {
        super(context);
        setEndpointClass(EnturGooglePubsubEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        String[] parts = remaining.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Google PubSub Endpoint format \"projectId:destinationName[:subscriptionName]\"");
        }

        GooglePubsubEndpoint pubsubEndpoint = new EnturGooglePubsubEndpoint(uri, this, remaining);
        pubsubEndpoint.setProjectId(parts[0]);
        pubsubEndpoint.setDestinationName(parts[1]);

        setProperties(pubsubEndpoint, parameters);

        return pubsubEndpoint;
    }
}
