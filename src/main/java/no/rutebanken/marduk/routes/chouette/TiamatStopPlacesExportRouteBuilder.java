package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.ExportJob;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;


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

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:tiamatStopPlacesExport").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Tiamat export stop places for provider with id ${header.tiamatProviderId}")
                .process(e -> {
                    Object tiamatProviderId = e.getIn().getHeaders().get("tiamatProviderId");
                    log.info("Tiamat Stop Places Export : launching export for provider " + tiamatProviderId.toString());
                    URL url = new URL(stopPlacesExportUrl.replace("http4", "http") + "/initiate?providerId=" + tiamatProviderId.toString());
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    e.getIn().setBody(con.getInputStream());

                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    // required to skip chouette reports parsing when polling job status
                    e.getIn().setHeader(Constants.SKIP_JOB_REPORTS, "true");
                    String tiamatJobStatusUrl = stopPlacesExportUrl + "/" + exportJob.getId() + "/status";
//                    setExportPollingHeaders(e, exportJob.getId().toString(), tiamatJobStatusUrl, TIAMAT_EXPORT_ROUTING_DESTINATION);
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, tiamatJobStatusUrl);
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, exportJob.getId());
                    log.info("Tiamat Stop Places Export  : export parsed => " + exportJob.getId() + " : " + tiamatJobStatusUrl);
                })

                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant(TIAMAT_EXPORT_ROUTING_DESTINATION))
                .setHeader(CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT.name()))
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("tiamat-stop-places-export-job");

        // called after a tiamat stop places export has been terminated (see CHOUETTE_JOB_STATUS_ROUTING_DESTINATION above and route direct:checkJobStatus)
        from(TIAMAT_EXPORT_ROUTING_DESTINATION).streamCaching()
                .log(LoggingLevel.INFO, getClass().getName(), "Tiamat process export results for provider with id ${header.tiamatProviderId}")
                // upload file directly from filesystem (solid?) to consumers , rather than downloading it from its api endpoint
                // .toD("${header.data_url}") // => this would be the download from api endpoint version
                .process(e -> {
                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    File file = fileSystemService.getTiamatFile(exportJob.getFileName());
                    log.info("Tiamat Stop Places Export  : export parsed => " + exportJob.getId() + " : " + exportJob.getJobUrl() + " file => " + file + " => " + file.exists());
                    e.getIn().setHeader("fileName", file.getName());
                    FileSystemResource fsr = new FileSystemResource(file);
                    e.getIn().setBody(fsr.getInputStream());
                })
                .process(exportToConsumersProcessor)
                .routeId("tiamat-stop-places-export-result-job");
    }

}
