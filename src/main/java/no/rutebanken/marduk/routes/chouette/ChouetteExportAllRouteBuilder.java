package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Component
public class ChouetteExportAllRouteBuilder extends AbstractChouetteRouteBuilder {


    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteExportAll").streamCaching()
            .transacted()
            .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette all export for provider with id ${header." + PROVIDER_ID + "}")
            .process(e -> {
                // Add correlation id only if missing
                e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
            })
            .to("activemq:queue:ChouettePollStatusQueue")
            .routeId("chouette-send-export-all-job");


    }


}
