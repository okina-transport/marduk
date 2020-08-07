package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.ExportJob;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.LoggingLevel;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import java.io.File;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;


/**
 * Exports stop places for specific provider
 */
@Component
public class TiamatStopPlacesExportRouteBuilder extends AbstractChouetteRouteBuilder {

    private static final String TIAMAT_EXPORT_ROUTING_DESTINATION = "direct:processTiamatExportResult";

    @Value("${stop-places-export.api.url}")
    private String stopPlacesExportUrl;

    @Autowired
    FileSystemService fileSystemService;

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
                .toD(stopPlacesExportUrl + "/initiate?providerId=${header.tiamatProviderId}")
                .unmarshal(xmlDataFormat)
                .process(e -> {
                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    File file = fileSystemService.getTiamatFile(exportJob.getFileName());
                    // required to skip chouette reports parsing when polling job status
                    e.getIn().setHeader(Constants.SKIP_JOB_REPORTS, "true");
                    String tiamatJobStatusUrl = stopPlacesExportUrl + "/" + exportJob.getId() + "/status";
//                    setExportPollingHeaders(e, exportJob.getId().toString(), tiamatJobStatusUrl, TIAMAT_EXPORT_ROUTING_DESTINATION);
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, tiamatJobStatusUrl);
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, exportJob.getId());
                    log.info("Tiamat Stop Places Export  : export parsed => " + exportJob.getId() + " : " + tiamatJobStatusUrl + " file => " + file + " => " + file.exists());
                })

                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant(TIAMAT_EXPORT_ROUTING_DESTINATION))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT.name()))

                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("tiamat-stop-places-export-job");

        from(TIAMAT_EXPORT_ROUTING_DESTINATION).streamCaching()
                .log(LoggingLevel.INFO, getClass().getName(), "Tiamat process export results for provider with id ${header.tiamatProviderId}")
                .process(e -> {
                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    File file = fileSystemService.getTiamatFile(exportJob.getFileName());
                    log.info("Tiamat Stop Places Export  : export parsed => " + exportJob.getId() + " : " + exportJob.getJobUrl() + " file => " + file + " => " + file.exists());
                })
                ;
    }

}
