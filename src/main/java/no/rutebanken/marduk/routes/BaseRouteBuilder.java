package no.rutebanken.marduk.routes;

import org.apache.camel.builder.RouteBuilder;

import java.net.ConnectException;


/**
 * Defines common route behavior.
 */
public class BaseRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel("activemq:queue:DeadLetterQueue")
            .maximumRedeliveries(3)
            .redeliveryDelay(3000));

        onException(ConnectException.class)
            .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

    }

}
