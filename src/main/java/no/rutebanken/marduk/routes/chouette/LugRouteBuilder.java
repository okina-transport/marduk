package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.STOP_OPERATORS_POST_PROCESS_NAME;

@Component
public class LugRouteBuilder extends BaseRouteBuilder {


    private int maxConsumers = 5;

    @Override
    public void configure() throws Exception {
        from("activemq:queue:PostProcessCompleted?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .log(LoggingLevel.INFO, "PostProcess completed")
                .choice()
                .when(header(EXPORT_FROM_TIAMAT).isEqualTo(true))
                    .to("direct:processTiamatExportEnd")
                .otherwise()
                    .to("direct:terminateChouettePostProcess")
                .end()
                .routeId("post-process-completed");

    }
}
