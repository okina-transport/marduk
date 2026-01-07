package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.Utils.getLastPathElementOfUrl;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;

@Component
public class ChouetteGenerateMapMatchingBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Autowired
    CreateMail createMail;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("jms:queue:ChouetteGenerateMapMatchingQueue?transacted=true").streamCache(Boolean.TRUE)
                .transacted()
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette map matching")
                .process(e -> {
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    e.getIn().removeHeader(Constants.JOB_ID);
                    JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_MAP_MATCHING).state(JobEvent.State.PENDING).build();
                    Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    String mapMatching = e.getIn().getHeader(GENERATE_MAP_MATCHING, String.class);
                    String mapmatchingParameters =  Parameters.getMapMatchingParameters(provider, provider.chouetteInfo.referential, mapMatching);
                    e.getIn().setHeader(JSON_PART, mapmatchingParameters);
                })
                .to(ROUTE_UPDATE_STATUS)
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .to("direct:assertHeadersForChouetteMapMatching")
                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .process(this::toGenericChouetteMultipart)
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/mapmatching")
                .process(e -> {
                    e.getIn().setHeader(Constants.JOB_STATUS_URL, e.getIn().getHeader("Location").toString());
                    e.getIn().setHeader(Constants.JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processMapMatchingResult"))
                .process(e -> e.getIn().setHeader(Constants.JOB_STATUS_JOB_TYPE, JobEvent.TimetableAction.BUILD_MAP_MATCHING.name()))
                .removeHeader("loopCounter")
                .to("jms:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-map-matching-job");

        from("direct:assertHeadersForChouetteMapMatching")
                .choice()
                    .when(simple("${header." + CHOUETTE_REFERENTIAL + "} == null || ${header." + PROVIDER_ID + "} == null "))
                        .log(LoggingLevel.WARN, correlation() + "Unable to start Chouette map matching for missing referential or providerId")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_MAP_MATCHING).state(JobEvent.State.FAILED).build())
                    .to(ROUTE_UPDATE_STATUS)
                    .stop()
                .end()
                .routeId("chouette-send-map-matching-job-validate-headers");

        // Will be sent here after polling completes
        from("direct:processMapMatchingResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setBody(constant(""))
                .choice()
                    .when(simple("${header.action_report_result} == 'OK'"))
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_MAP_MATCHING).state(JobEvent.State.OK).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null &&
                                    !e.getIn().getHeader(WORKLOW, String.class).equals("VALIDATION") &&
                                    !e.getIn().getHeader(WORKLOW, String.class).equals("EXPORT")) {
                                createMail.createMail(e, null, JobEvent.TimetableAction.BUILD_MAP_MATCHING, true);
                            }
                        })
                        .to(ROUTE_UPDATE_STATUS)
                        .to("direct:checkScheduledJobsBeforeTriggeringTransfer")
                    .when(simple("${header.action_report_result} == 'NOK'"))
                        .log(LoggingLevel.INFO, correlation() + "Map matching failed")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_MAP_MATCHING).state(JobEvent.State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, JobEvent.TimetableAction.BUILD_MAP_MATCHING, false);
                            }
                        })
                        .to(ROUTE_UPDATE_STATUS)
                    .otherwise()
                        .log(LoggingLevel.ERROR, correlation() + "Map matching went wrong")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_MAP_MATCHING).state(JobEvent.State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, JobEvent.TimetableAction.BUILD_MAP_MATCHING, false);
                            }
                        })
                        .to(ROUTE_UPDATE_STATUS)
                .end()
                .routeId("chouette-process-map-matching-status");

        // Check that no other map matching jobs in status SCHEDULED exists for this referential. If so, do not trigger transfer
        from("direct:checkScheduledJobsBeforeTriggeringTransfer")
                .setProperty("job_status_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?timetableAction=importer&status=SCHEDULED&status=STARTED"))
                .toD("${exchangeProperty.job_status_url}")
                .choice()
                    .when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
                        .when(e -> "VALIDATION".equals(e.getIn().getHeader(WORKLOW, String.class)) || "EXPORT".equals(e.getIn().getHeader(WORKLOW, String.class)))
                            .log(LoggingLevel.INFO, correlation() + "Map matching ok, transfering data to next dataspace")
                            .to("jms:queue:ChouetteTransferExportQueue")
                    .end()
                .routeId("chouette-process-job-list-after-map-matching");
    }
}
