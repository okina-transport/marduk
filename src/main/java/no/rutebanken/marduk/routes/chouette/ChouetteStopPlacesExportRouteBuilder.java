package no.rutebanken.marduk.routes.chouette;

import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;


/**
 * Exports stop places for specific provider
 */
public class ChouetteStopPlacesExportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${stop-places-export.api.url}")
    private String stopPlacesExportUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteStopPlacesExport").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette export stop places for provider with id ${header." + PROVIDER_ID + "}")
                .toD(stopPlacesExportUrl + "?providerId= ${header." + PROVIDER_ID + "}")
                .routeId("chouette-stop-places-export-job");
    }

}
