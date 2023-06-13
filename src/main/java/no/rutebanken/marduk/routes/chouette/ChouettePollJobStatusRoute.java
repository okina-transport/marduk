/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.ExportJob;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.JobResponseWithLinks;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.chouette.mapping.ProviderAndJobsMapper;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.Status.ABORTED;
import static no.rutebanken.marduk.routes.chouette.json.Status.CANCELED;
import static no.rutebanken.marduk.routes.chouette.json.Status.RESCHEDULED;
import static no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED;
import static no.rutebanken.marduk.routes.chouette.json.Status.STARTED;

@Component
public class ChouettePollJobStatusRoute extends AbstractChouetteRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());


    @Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${lug.url}")
    private String lugUrl;

    private int maxConsumers = 5;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;


    /**
     * This routebuilder polls a job until it is terminated. It expects a few headers set on the message it receives:
     * Constants.CHOUETTE_JOB_STATUS_URL - the url to poll
     * Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION - a routing slip which defines where to send poll result
     * .. and a few more related to status updates
     */

    @SuppressWarnings("unchecked")
    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteGetJobsForProvider")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for provider id '${header." + PROVIDER_ID + "}'")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs"))
                .to("direct:chouetteGetJobs")
                .routeId("chouette-list-jobs-for-provider");

        from("direct:chouetteGetJobs")
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .process(e -> {
                    String url = (String) e.getProperty("chouette_url");

                    // Convert camel dynamic endpoint format (http4:xxxx) to url (http://xxx) before manipulating url. Needed as interception
                    // does not seem to work with // in target anymore (as of camel 2.22.0)
                    boolean dynamicEndpointNotation=!url.contains("://");
                    if (dynamicEndpointNotation) {
                        url = url.replaceFirst(":", "://");
                    }
                    URIBuilder b = new URIBuilder(url);
                    if (e.getIn().getHeader("action") != null) {
                        b.addParameter("action", (String) e.getIn().getHeader("action"));
                    }
                    if (e.getIn().getHeader("status") != null) {

                        Object status = e.getIn().getHeader("status");
                        if (status instanceof List) {
                            for (String s : (List<String>) status) {
                                b.addParameter("status", s);
                            }
                        } else {
                            b.addParameter("status", (String) status);
                        }
                    }
                    b.addParameter("addActionParameters", Boolean.FALSE.toString());
                    String newUri = b.toString();
                    e.setProperty("chouette_url", newUri);
                })
                .toD("${exchangeProperty.chouette_url}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse[].class)
                .routeId("chouette-list-jobs");


        from("direct:chouetteCancelJob")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.DELETE))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/scheduled_jobs/${header." + Constants.CHOUETTE_JOB_ID + "}"))
                .toD("${exchangeProperty.chouette_url}")
                .setBody(constant(null))
                .routeId("chouette-cancel-job");

        from("direct:chouetteCancelAllJobsForProvider")
                .process(e -> e.getIn().setHeader("status", Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:chouetteGetJobsForProvider")
                .sort(body(), new JobResponseDescendingSorter())
                .removeHeaders("Camel*")
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(Constants.CHOUETTE_JOB_ID, simple("${body.id}"))
                .setBody(constant(null))
                .to("direct:chouetteCancelJob")
                .routeId("chouette-cancel-all-jobs-for-provider");

        from("direct:chouetteCancelAllJobsForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(Constants.PROVIDER_ID, simple("${body.id}"))
                .setBody(constant(null))
                .removeHeaders("Camel*")
                .to("direct:chouetteCancelAllJobsForProvider")
                .routeId("chouette-cancel-all-jobs-for-all-providers");

        from("direct:chouetteGetJobsAll")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for all providers}'")
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/jobs"))
                .to("direct:chouetteGetJobs")
                .process(e -> {
                    JobResponse[] jobs = e.getIn().getBody(JobResponse[].class);
                    e.getIn().setBody(new ProviderAndJobsMapper().mapJobResponsesToProviderAndJobs(jobs, getProviderRepository().getProviders()));
                })
                .end()
                .routeId("chouette-get-jobs-all");



        from("activemq:queue:PostProcessCompleted?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .log(LoggingLevel.INFO, "PostProcess completed")
                .process(e -> {
                    Object netexGlobalRaw = e.getIn().getHeader(NETEX_EXPORT_GLOBAL);
                    Object simulationExpRaw = e.getIn().getHeader(IS_SIMULATION_EXPORT);

                    e.getIn().setHeader(NETEX_EXPORT_GLOBAL,convertToBoolean(netexGlobalRaw));
                    e.getIn().setHeader(IS_SIMULATION_EXPORT,convertToBoolean(simulationExpRaw));
                })
                .process(exportToConsumersProcessor)
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.OK).build())
                .to("direct:updateStatus")
                .routeId("post-process-completed");

        from("activemq:queue:ChouettePollStatusQueue?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .transacted()
                .validate(header(Constants.CORRELATION_ID).isNotNull())
                .validate(header(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .validate(header(Constants.CHOUETTE_JOB_STATUS_URL).isNotNull())
                .validate(header(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE).isNotNull())
                .to("direct:checkJobStatus")
                .routeId("chouette-validate-job-status-parameters");

        from("direct:checkJobStatus")
                .process(e -> {
                    e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1);
                })
                .setProperty(Constants.CHOUETTE_REFERENTIAL, header(Constants.CHOUETTE_REFERENTIAL))
                .setProperty("url", header(Constants.CHOUETTE_JOB_STATUS_URL))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .process(e -> {
                    URL url = new URL(e.getProperty("url").toString().replace("http4", "http"));
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    e.getIn().setBody(con.getInputStream());
                })
                .choice()
                    .when(simple("${header.TIAMAT_STOP_PLACES_EXPORT} != null || ${header.TIAMAT_POINTS_OF_INTEREST_EXPORT} != null || ${header.TIAMAT_PARKINGS_EXPORT} != null"))
                        .process(e -> {

                            final Boolean isPOI = e.getIn().getHeader(Constants.TIAMAT_POINTS_OF_INTEREST_EXPORT) != null;
                            final Boolean isParkings = e.getIn().getHeader(Constants.TIAMAT_PARKINGS_EXPORT) != null;

                            String skipJobReportsJobId;

                            if (isPOI) {
                                skipJobReportsJobId = e.getIn().getHeader(Constants.TIAMAT_POINTS_OF_INTEREST_EXPORT).toString();
                            } else if (isParkings) {
                                skipJobReportsJobId = e.getIn().getHeader(Constants.TIAMAT_PARKINGS_EXPORT).toString();
                            } else {
                                skipJobReportsJobId = e.getIn().getHeader(Constants.TIAMAT_STOP_PLACES_EXPORT).toString();
                            }

                            String exportJobId = e.getIn().getHeader(Constants.CHOUETTE_JOB_ID) != null ? e.getIn().getHeader(Constants.CHOUETTE_JOB_ID).toString() : null;
                            if (skipJobReportsJobId.equals(exportJobId)) {
                                if (isPOI) {
                                    log.info("TIAMAT_POINTS_OF_INTEREST_EXPORT matching job ids : " + skipJobReportsJobId);
                                } else if (isParkings) {
                                    log.info("TIAMAT_PARKINGS_EXPORT matching job ids : " + skipJobReportsJobId);
                                } else {
                                    log.info("TIAMAT_STOP_PLACES_EXPORT matching job ids : " + skipJobReportsJobId);
                                }
                                boolean isExportDone = false;
                                if(JobEvent.TimetableAction.EXPORT.name().equals(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))) {
                                    ExportJob exportJob = e.getIn().getBody(ExportJob.class);
                                    e.getProperties().put("STATUS", exportJob.getStatus().name());
                                    e.getIn().setBody(exportJob);
                                    isExportDone = exportJob.getStatus().isDone();
                                } else if(JobEvent.TimetableAction.EXPORT_NETEX.name().equals(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))) {
                                    String json = e.getIn().getBody(String.class);
                                    JobResponse jobResponse = new ObjectMapper().readValue(json, JobResponse.class);
                                    isExportDone = jobResponse.getStatus().isDone();
                                    if(Status.TERMINATED == jobResponse.getStatus()) {
                                        e.getProperties().put("STATUS", "FINISHED");
                                        e.getIn().setHeader("action_report_result", "OK");
                                        e.getIn().setBody(json);
                                    }
                                    else {
                                        e.getProperties().put("STATUS", jobResponse.getStatus().name());
                                    }
                                }

                                // remove header to ensure we don't fall into a "reschedulejob infinite loop" on aborted/failed jobs
                                if (isExportDone) {
                                    e.getIn().removeHeader(Constants.TIAMAT_STOP_PLACES_EXPORT);
                                    e.getIn().removeHeader(Constants.TIAMAT_POINTS_OF_INTEREST_EXPORT);
                                    e.getIn().removeHeader(Constants.TIAMAT_PARKINGS_EXPORT);
                                }
                            } else {
                                log.warn("ERROR : a non tiamat job should not trigger this camel process. Non matching job ids => " + skipJobReportsJobId + " vs " + exportJobId + " XXXXXXXXXX SHOULD NOT HAPPEN !!");
                                e.getIn().removeHeader(Constants.TIAMAT_STOP_PLACES_EXPORT);
                                e.getIn().removeHeader(Constants.TIAMAT_POINTS_OF_INTEREST_EXPORT);
                                e.getIn().removeHeader(Constants.TIAMAT_PARKINGS_EXPORT);
                            }
                        })
                        .choice()
                            .when(simple("${exchangeProperty.STATUS} == 'FINISHED'"))
                                .toD("${header." + Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION + "}")
                            .otherwise()
                                .to("direct:rescheduleJob")
                        .endChoice()
                        .stop()
                    .otherwise()
                        .unmarshal().json(JsonLibrary.Jackson, JobResponseWithLinks.class)
                .end()

                .setProperty("current_status", simple("${body.status}"))
                .choice()
                    .when(PredicateBuilder.and(simple("${body.status} == ${type:no.rutebanken.marduk.routes.chouette.json.Status.TERMINATED}"),  simple("${header." + POST_PROCESS + "} != null")))
                        .to("direct:sendToLUG")
                    .when(PredicateBuilder.or(simple("${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.STARTED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.RESCHEDULED}"),
                            simple("${header.loopCounter} > " + maxRetries)))
                        .to("direct:jobStatusDone")
                .otherwise()
                     // Update status
                    .to("direct:rescheduleJob")
                .end()
                .routeId("chouette-get-job-status");


        from("direct:rescheduleJob")
                .choice()
                .when(simple("${exchangeProperty.current_status} == '" + STARTED + "' && ${header.loopCounter} == 1"))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.STARTED).jobId(e.getIn().getHeader(Constants.CHOUETTE_JOB_ID, Long.class)).build())
                .to("direct:updateStatus")
                .end()
                .setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY, constant(retryDelay))
                // Remove or ActiveMQ will think message is overdue and resend immediately
                .removeHeader("scheduledJobId")
                .setBody(constant(""))
                //.log(LoggingLevel.INFO,"Scheduling next polling message in ${header."+ActiveMQMessage.AMQ_SCHEDULED_DELAY+"}ms")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-reschedule-job");


        from("direct:sendToLUG")
                .log(LoggingLevel.INFO, correlation() + "Process completed. Sending job to LUG")
                .setBody().constant(null)
                .toD(lugUrl + "/api/mobi-iti/post-process")
                .routeId("send-to-LUG");

        from("direct:jobStatusDone")
                .log(LoggingLevel.DEBUG, correlation() + "Exited retry loop with status ${header.current_status}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .inOnly("direct:handleGlobalNetexExportCase")
                .choice()
                    .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "' || ${header.current_status} == '" + RESCHEDULED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.TIMEOUT).build())
                        .to("direct:updateStatus")
                        .stop()
                    .when(simple("${header.current_status} == '" + ABORTED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state FAILED. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.FAILED).build())
                        .to("direct:updateStatus")
                        .stop()
                    .when(simple("${header.current_status} == '" + CANCELED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state CANCELLED. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.CANCELLED).build())
                        .to("direct:updateStatus")
                        .stop()
                .end()
                .process(e -> {
                    JobResponseWithLinks response = e.getIn().getBody(JobResponseWithLinks.class);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.getIn().setHeader("action_report_url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                    Optional<String> validationReportUrlOptional = response.links.stream().filter(li -> "validation_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.getIn().setHeader("validation_report_url", validationReportUrlOptional.orElse(null));
                    Optional<String> dataUrlOptional = response.links.stream().filter(li -> "data".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.getIn().setHeader("data_url", dataUrlOptional.orElse(null));
                })
                // Fetch and parse action report
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling action report url ${header.action_report_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${header.action_report_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")

                .doTry()
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class).process(e -> { /** Dummy line to make doCatch available */})
                .doCatch(JsonMappingException.class)
                .log(LoggingLevel.WARN, correlation() + "Received invalid (empty?) action report for terminated job. Giving up.")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.FAILED).build())
                .to("direct:updateStatus")
                .stop()
                .end()

                .choice()
                    .when(simple("${body.finalised} == false"))
                        .choice()
                            .when(simple("${header.loopCounter} > " + maxRetries))
                                .log(LoggingLevel.WARN, correlation() + "Received non-finalised action report for terminated job. Giving up.")
                                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.FAILED).build())
                                .to("direct:updateStatus")
                            .otherwise()
                                .log(LoggingLevel.INFO, correlation() + "Received non-finalised action report for terminated job. Waiting before retry ")
                                // Update status
                                .to("direct:rescheduleJob")
                        .end()
                    .stop()
                .end()
                .process(e -> e.getIn().setHeader("action_report_result", e.getIn().getBody(ActionReportWrapper.class).actionReport.result))
                // Fetch and parse validation report
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                    .when(simple("${header.validation_report_url} != null"))
                        .log(LoggingLevel.DEBUG, correlation() + "Calling validation report url ${header.validation_report_url}")
                        .removeHeaders("Camel*")
                        .setBody(simple(""))
                        .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                        .toD("${header.validation_report_url}")
                        .to("direct:checkValidationReport")
                    .otherwise()
                        .setHeader("validation_report_result", constant("NOT_PRESENT"))
                        .toD("${header." + CHOUETTE_JOB_STATUS_ROUTING_DESTINATION + "}")
                .end()
                .routeId("chouette-process-job-reports");

        from("direct:checkValidationReport")
                .choice()
                    .when()
                        .jsonpath("$.validation_report.check_points[?(@.severity == 'ERROR' && @.result == 'NOK')]")
                        .setHeader("validation_report_result", constant("NOK"))
                    .otherwise()
                        .setHeader("validation_report_result", constant("OK"))
                .end()
                .log(LoggingLevel.DEBUG, correlation() + "action_report_result=${header.action_report_result} validation_report_result=${header.validation_report_result}")
                .toD("${header." + Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION + "}")
                .routeId("chouette-process-validation-report");


        from("direct:handleGlobalNetexExportCase")
                .choice()
                    .when(e->  e.getIn().getHeader(NETEX_EXPORT_GLOBAL) != null && (boolean) e.getIn().getHeader(NETEX_EXPORT_GLOBAL))
                        .inOnly("direct:updateMergedNetexStatus")
                .end()
                .routeId("handle-global-netex-export-case");
    }

    private Boolean convertToBoolean (Object rawProperty){
        if (rawProperty instanceof Boolean){
            return (Boolean) rawProperty;
        }

        if (rawProperty instanceof String){
            return Boolean.parseBoolean((String)rawProperty);
        }
        logger.error("Unable to cast object to boolean:" + rawProperty);
        return null;
    }

}


