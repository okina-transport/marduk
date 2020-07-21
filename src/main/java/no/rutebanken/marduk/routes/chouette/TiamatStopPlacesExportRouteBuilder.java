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
public class TiamatStopPlacesExportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${stop-places-export.api.url}")
    private String stopPlacesExportUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        // XML Data Format
        JaxbDataFormat xmlDataFormat = new JaxbDataFormat();
        JAXBContext con = JAXBContext.newInstance(ExportJob.class);
        xmlDataFormat.setContext(con);


        from("direct:tiamatStopPlacesExport").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Tiamat export stop places for provider with id ${header.tiamatProviderId}")
                .process(e -> {
                    Object tiamatProviderId = e.getIn().getHeaders().get("tiamatProviderId");
                    log.info("Tiamat Stop Places Export : launching export for provider " + tiamatProviderId.toString());
                })
                .toD(stopPlacesExportUrl + "?providerId=${header.tiamatProviderId}")
                .unmarshal(xmlDataFormat)
                .process(e -> {
                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    log.info("Tiamat Stop Places Export  : export parsed => " + exportJob.getId() + " : " + exportJob.getJobUrl());
                })
                .routeId("tiamat-stop-places-export-job");
    }

}
