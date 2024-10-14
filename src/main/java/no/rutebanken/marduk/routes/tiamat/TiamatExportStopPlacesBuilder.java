package no.rutebanken.marduk.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.chouette.ExportToConsumersProcessor;
import no.rutebanken.marduk.routes.chouette.UpdateExportTemplateProcessor;
import no.rutebanken.marduk.routes.chouette.json.Job;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.http.common.HttpMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;


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

    @Autowired
    UpdateExportTemplateProcessor updateExportTemplateProcessor;

    @Value("${lug.url}")
    private String lugUrl;

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
                        e.getIn().getHeaders().put(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
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

                    Job job = e.getIn().getBody(Job.class);
                    e.getIn().getHeaders().put(FILE_NAME,  job.getFileName());
                    // required to skip chouette reports parsing when polling job status
                    e.getIn().setHeader(Constants.TIAMAT_STOP_PLACES_EXPORT, job.getId());
                    String tiamatJobStatusUrl = stopPlacesExportUrl + "/" + job.getId() + "/status";
                    e.getIn().setHeader(JOB_STATUS_URL, tiamatJobStatusUrl);
                    e.getIn().setHeader(Constants.JOB_ID, job.getId());
                    log.info("Tiamat Stop Places Export  : export parsed => " + job.getId() + " : " + tiamatJobStatusUrl);
                    log.info("Lancement export Arrêts - Fichier : " + job.getFileName() + " - Espace de données : " + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                })

                .setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant(TIAMAT_EXPORT_ROUTING_DESTINATION))
                .setHeader(JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT.name()))
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("tiamat-stop-places-export-job");

        // called after a tiamat stop places export has been terminated (see CHOUETTE_JOB_STATUS_ROUTING_DESTINATION above and route direct:checkJobStatus)
        from(TIAMAT_EXPORT_ROUTING_DESTINATION).streamCaching()
                .log(LoggingLevel.INFO,"Export Arrêts terminé - Fichier : ${header." + FILE_NAME + "} - Espace de données : ${header." + CHOUETTE_REFERENTIAL + "}")
                .log(LoggingLevel.INFO, getClass().getName(), "Tiamat process export results for provider with id ${header.tiamatProviderId}")
                .setHeader(EXPORT_FROM_TIAMAT, simple("true"))
                .process(exportToConsumersProcessor)
                .process(updateExportTemplateProcessor)
                .choice()
                .when(header(POST_PROCESS).isEqualTo(STOP_OPERATORS_POST_PROCESS_NAME))
                    .to("direct:sendStopPlaceExportToLug")
                .otherwise()
                    .to("direct:processTiamatExportEnd")
                .end()

                .routeId("tiamat-stop-places-export-result-job");

        from("direct:sendStopPlaceExportToLug")
                .log(LoggingLevel.INFO, "Launching post process.")
                .process(e ->{
                    Job job = e.getIn().getBody(Job.class);
                    e.getIn().setHeader(SUB_FOLDER, job.getSubFolder());
                })
                .setBody().constant(null)
                .removeHeaders("CamelHttp*")
                .removeHeaders("Authorization*")
                .removeHeaders("Camel*")
                .toD(lugUrl + "/api/mobi-iti/post-process")
                .routeId("tiamat-stop-places-send-to-lug");

        from("direct:processTiamatExportEnd")
                .choice()
                .when(header(CHOUETTE_REFERENTIAL).isEqualTo("mobiiti_technique"))
                .process(e -> {
                    File file = fileSystemService.getTiamatFile(e);
                    FileSystemResource fsr = new FileSystemResource(file);
                    e.getIn().setBody(fsr.getInputStream());
                })
                .log(LoggingLevel.INFO, "Sending of the netex file stops generated by mobiiti_technique => " + MERGED_NETEX_STOPS_ROOT_DIR + "/CurrentAndFuture_latest.zip")
                .setHeader(FILE_HANDLE, simple(MERGED_NETEX_STOPS_ROOT_DIR + "/CurrentAndFuture_latest.zip"))
                .to("direct:uploadBlob")
                .end()
                .routeId("tiamat-stop-places-export-end-process");
    }

}
