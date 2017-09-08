package no.rutebanken.marduk.pubsub;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.Subscription;
import com.google.api.services.pubsub.model.Topic;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnturGooglePubsubEndpoint extends GooglePubsubEndpoint {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public EnturGooglePubsubEndpoint(String uri, Component component, String remaining) {
        super(uri, component, remaining);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        afterPropertiesSet();
        // TODO disable by default?
        createSubscriptionIfMissing();
        return super.createConsumer(new EnturGooglePubSubProcessorWrapper(processor));
    }

    @Override
    public Producer createProducer() throws Exception {
        afterPropertiesSet();
        return new EnturGooglePubsubProducer(this);
    }

    private void createSubscriptionIfMissing() throws Exception {

        String topicFullName = String.format("projects/%s/topics/%s",
                getProjectId(),
                getDestinationName());

        String subscriptionFullName = String.format("projects/%s/subscriptions/%s",
                getProjectId(),
                getDestinationName());

        Pubsub pubsub = getPubsub();

        try {
            pubsub.projects()
                    .topics()
                    .create(topicFullName, new Topic())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            // 409 indicates that the resource is available already
            if (409 == e.getStatusCode()) {
                logger.debug("Did not create topic: " + topicFullName + " ,as it already exists");
            } else {
                throw e;
            }
        }

        try {
            Subscription subscription = new Subscription()
                                                .setTopic(topicFullName)
                                                .setAckDeadlineSeconds(0);

            pubsub.projects()
                    .subscriptions()
                    .create(subscriptionFullName, subscription)
                    .execute();
        } catch (GoogleJsonResponseException e) {
            // 409 indicates that the resource is available already
            if (409 == e.getStatusCode()) {
                logger.debug("Did not create subscription: " + subscriptionFullName + " ,as it already exists");
            } else {
                throw e;
            }
        }


    }
}