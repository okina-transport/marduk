package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.Utils.PollJobStatusRoute;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.STOP_OPERATORS_POST_PROCESS_NAME;

@Component
public class LugRouteBuilder extends BaseRouteBuilder {


    private int maxConsumers = 5;

    @Autowired
    PollJobStatusRoute pollJobStatusRoute;

    @Override
    public void configure() throws Exception {
        from("activemq:queue:PostProcessCompleted?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .log(LoggingLevel.INFO, "PostProcess completed")
                .process(e -> {
                    Object netexGlobalRaw = e.getIn().getHeader(NETEX_EXPORT_GLOBAL);
                    Object simulationExpRaw = e.getIn().getHeader(IS_SIMULATION_EXPORT);

                    e.getIn().setHeader(NETEX_EXPORT_GLOBAL, pollJobStatusRoute.convertToBoolean(netexGlobalRaw));
                    e.getIn().setHeader(IS_SIMULATION_EXPORT, pollJobStatusRoute.convertToBoolean(simulationExpRaw));
                })
                .choice()
                .when(header(JOB_STATUS_JOB_TYPE).isEqualTo("EXPORT_NETEX"))
                    .to("direct:processNetexExportResultEnd")
                .when(header(EXPORT_FROM_TIAMAT).isEqualTo(true))
                    .to("direct:processTiamatExportEnd")
                .otherwise()
                    .to("direct:terminateChouettePostProcess")
                .end()
                .routeId("post-process-completed");
    }
}
