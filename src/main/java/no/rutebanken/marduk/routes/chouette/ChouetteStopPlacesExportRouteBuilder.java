package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;


/**
 * Exports stop places for specific provider
 */
@Component
public class ChouetteStopPlacesExportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${stop-places-export.api.url}")
    private String stopPlacesExportUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteStopPlacesExport").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette export stop places for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    Object providerIdS = e.getIn().getHeaders().get("providerId");
                    Object providerIdL = e.getIn().getHeaders().get("providerIdLong");
                    log.info("chouetteStopPlacesExport : processing export ....");
                })
                .toD(stopPlacesExportUrl + "?providerId=${header.providerIdLong}")
                .process(e -> {
                    log.info("chouetteStopPlacesExport : tiamat export response ....");
                })
                .routeId("chouette-stop-places-export-job");
    }

}
