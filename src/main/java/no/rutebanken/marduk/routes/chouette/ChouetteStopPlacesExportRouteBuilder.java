package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.ExportJob;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;

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

        // XML Data Format
        JaxbDataFormat xmlDataFormat = new JaxbDataFormat();
        JAXBContext con = JAXBContext.newInstance(ExportJob.class);
        xmlDataFormat.setContext(con);


        from("direct:chouetteStopPlacesExport").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette export stop places for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    Object providerIdS = e.getIn().getHeaders().get("providerId");
                    Object providerIdL = e.getIn().getHeaders().get("providerIdLong");
                    log.info("chouetteStopPlacesExport : processing export ....");
                })
                .toD(stopPlacesExportUrl + "?providerId=${header.providerIdLong}")
                .unmarshal(xmlDataFormat)
                .process(e -> {
                    log.info("chouetteStopPlacesExport : tiamat export parsed");
                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    log.info("chouetteStopPlacesExport : tiamat export parsed => " + exportJob.getId() + " : " + exportJob.getJobUrl());
                })
                .routeId("tiamat-stop-places-export-job");
    }

}
