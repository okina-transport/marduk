package no.rutebanken.marduk.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.chouette.ExportToConsumersProcessor;
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
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.EXPORT_FILE_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;


/**
 * Exports stop places for specific provider
 */
@Component
public class TiamatExportStopPlacesBuilder extends AbstractChouetteRouteBuilder {

    private static final String TIAMAT_EXPORT_ROUTING_DESTINATION = "direct:processTiamatExportResult";

    @Value("${stop-places-export.api.url}")
    private String stopPlacesExportUrl;

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:TiamatStopPlacesExport").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Tiamat export stop places for provider with id ${header.tiamatProviderId}")
                .choice()
                    .when(simple("${header.RutebankenOriginalProviderId} == null"))
                    .log(LoggingLevel.INFO, "Valorisation du tiamatProviderId avec RutebankenOriginialProviderId")
                    .process(e -> {
                        Long tiamatProviderId = Long.valueOf(e.getIn().getHeaders().get(PROVIDER_ID).toString());
                        e.getIn().getHeaders().put("tiamatProviderId", tiamatProviderId);
                    })
                .end()
                .choice()
                    .when(simple("${header.RutebankenCorrelationId} == null"))
                    .log(LoggingLevel.INFO, "Ajout d'un correlation id")
                    .process(e -> {
                        e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    })
                .end()
                .process(e -> {
                    Object tiamatProviderId = e.getIn().getHeaders().get("tiamatProviderId");
                    log.info("Tiamat Stop Places Export : launching export for provider " + tiamatProviderId.toString());
                    URL url = new URL(stopPlacesExportUrl.replace("http4", "http") + "/initiate?providerId=" + tiamatProviderId.toString());
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    e.getIn().setBody(con.getInputStream());

                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                    // required to skip chouette reports parsing when polling job status
                    e.getIn().setHeader(Constants.TIAMAT_STOP_PLACES_EXPORT, exportJob.getId());
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
                    File file = fileSystemService.getTiamatFile(exportJob.getSubFolder()+"/"+exportJob.getFileName());
                    log.info("Tiamat Stop Places Export  : export parsed => " + exportJob.getId() + " : " + exportJob.getJobUrl() + " file => " + file + " => " + file.exists());
                    e.getIn().setHeader("fileName", file.getName());
                    e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
                    FileSystemResource fsr = new FileSystemResource(file);
                    e.getIn().setBody(fsr.getInputStream());
                })
                .process(exportToConsumersProcessor)
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .to("direct:uploadBlobExport")
                .routeId("tiamat-stop-places-export-result-job");
    }

}
