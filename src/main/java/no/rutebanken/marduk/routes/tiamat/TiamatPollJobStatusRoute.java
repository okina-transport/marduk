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

package no.rutebanken.marduk.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import no.rutebanken.marduk.routes.chouette.*;
import no.rutebanken.marduk.routes.chouette.json.*;
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

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.Status.*;

@Component
public class TiamatPollJobStatusRoute extends AbstractChouetteRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());


    @Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${lug.url}")
    private String lugUrl;

    private int maxConsumers = 5;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Autowired
    UpdateExportTemplateProcessor updateExportTemplateProcessor;

    @Autowired
    CreateMail createMail;


    @Autowired
    private PrometheusMetricsService metrics;


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

        from("direct:tiamatGetJobsForProvider")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for provider id '${header." + PROVIDER_ID + "}'")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                })
                .setProperty("tiamat_url", simple(tiamatUrl + "/jobs/${header." + CHOUETTE_REFERENTIAL + "}"))
                .to("direct:tiamatGetJobs")
                .routeId("tiamat-list-jobs-for-provider");

        from("direct:tiamatGetJobs")
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .process(e -> {
                    String url = (String) e.getProperty("tiamat_url");

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
                    e.setProperty("tiamat_url", newUri);
                })
                .toD("${exchangeProperty.tiamat_url}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse[].class)
                .routeId("tiamat-list-jobs");


        from("direct:tiamatCancelJob")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.DELETE))
                .setProperty("tiamat_url", simple(tiamatUrl + "/jobs/${header." + CHOUETTE_REFERENTIAL + "}/scheduled_jobs/${header." + Constants.JOB_ID + "}"))
                .toD("${exchangeProperty.tiamat_url}")
                .setBody(constant(null))
                .routeId("tiamat-cancel-job");

        from("direct:tiamatCancelAllJobsForProvider")
                .process(e -> e.getIn().setHeader("status", Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:tiamatGetJobsForProvider")
                .sort(body(), new JobResponseDescendingSorter())
                .removeHeaders("Camel*")
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(Constants.JOB_ID, simple("${body.id}"))
                .setBody(constant(null))
                .to("direct:tiamatCancelJob")
                .routeId("tiamat-cancel-all-jobs-for-provider");

        from("direct:tiamatCancelAllJobsForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(Constants.PROVIDER_ID, simple("${body.id}"))
                .setBody(constant(null))
                .removeHeaders("Camel*")
                .to("direct:tiamatCancelAllJobsForProvider")
                .routeId("tiamat-cancel-all-jobs-for-all-providers");

        from("direct:chouetteGetJobsAll")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for all providers}'")
                .setProperty("chouette_url", simple(tiamatUrl + "/chouette_iev/referentials/jobs"))
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
                .process(updateExportTemplateProcessor)
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(Constants.JOB_STATUS_JOB_TYPE))).state(State.OK).build())
                .to("direct:updateStatus")
                .routeId("post-process-completed");

        from("activemq:queue:TiamatPollStatusQueue?transacted=true&maxConcurrentConsumers=" + maxConsumers)
//                .transacted()
                .process(e -> {
                    String toto = "";
                })
                .validate(header(Constants.CORRELATION_ID).isNotNull())
                .validate(header(Constants.JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .validate(header(Constants.JOB_STATUS_URL).isNotNull())
                .validate(header(Constants.JOB_STATUS_JOB_TYPE).isNotNull())
                .to("direct:tiamatCheckJobStatus")
                .routeId("tiamat-validate-job-status-parameters");

        from("direct:tiamatCheckJobStatus")
                .process(e -> {
                    e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1);
                })
                .setProperty(Constants.CHOUETTE_REFERENTIAL, header(Constants.CHOUETTE_REFERENTIAL))
                .setProperty("url", header(Constants.JOB_STATUS_URL))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .process(e -> {
                    URL url = new URL(e.getProperty("url").toString().replace("http4", "http"));
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    e.getIn().setBody(con.getInputStream());
                })
                .unmarshal().json(JsonLibrary.Jackson, JobResponseWithLinks.class)

                .setProperty("current_status", simple("${body.status}"))
                .choice()
                    .when(PredicateBuilder.and(simple("${body.status} == ${type:no.rutebanken.marduk.routes.chouette.json.Status.TERMINATED}"),  simple("${header." + POST_PROCESS + "} != null")))
//                        .to("direct:sendToLUG")
                    .when(PredicateBuilder.or(simple("${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.STARTED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.RESCHEDULED}"),
                            simple("${header.loopCounter} > " + maxRetries)))
                        .to("direct:tiamatJobStatusDone")
                .otherwise()
                     // Update status
                    .to("direct:tiamatRescheduleJob")
                .end()
                .routeId("tiamat-get-job-status");


        from("direct:tiamatRescheduleJob")
                .choice()
                .when(simple("${exchangeProperty.current_status} == '" + STARTED + "' && ${header.loopCounter} == 1"))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(Constants.JOB_STATUS_JOB_TYPE))).state(State.STARTED).jobId(e.getIn().getHeader(Constants.JOB_ID, Long.class)).build())
                .to("direct:updateStatus")
                .end()
                .setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY, constant(retryDelay))
                // Remove or ActiveMQ will think message is overdue and resend immediately
                .removeHeader("scheduledJobId")
                .setBody(constant(""))
                //.log(LoggingLevel.INFO,"Scheduling next polling message in ${header."+ActiveMQMessage.AMQ_SCHEDULED_DELAY+"}ms")
                .to("activemq:queue:tiamatPollStatusQueue")
                .routeId("tiamat-reschedule-job");

        from("direct:tiamatJobStatusDone")
                .log(LoggingLevel.DEBUG, correlation() + "Exited retry loop with status ${header.current_status}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .inOnly("direct:tiamatHandleGlobalNetexExportCase")
                .choice()
                    .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "' || ${header.current_status} == '" + RESCHEDULED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(JOB_STATUS_JOB_TYPE))).state(State.TIMEOUT).build())
                        .to("direct:updateStatus")
                        .stop()
                    .when(simple("${header.current_status} == '" + ABORTED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state FAILED. Stopping route.")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(JOB_STATUS_JOB_TYPE))).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, null, false);
                            }
                        })
                        .to("direct:updateStatus")
                        .stop()
                    .when(simple("${header.current_status} == '" + CANCELED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state CANCELLED. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(JOB_STATUS_JOB_TYPE))).state(State.CANCELLED).build())
                        .to("direct:updateStatus")
                        .stop()
                .end()
                // Fetch and parse action report
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .routeId("tiamat-process-job-reports");


        from("direct:tiamatHandleGlobalNetexExportCase")
                .choice()
                    .when(e->  e.getIn().getHeader(NETEX_EXPORT_GLOBAL) != null && (boolean) e.getIn().getHeader(NETEX_EXPORT_GLOBAL))
                        .inOnly("direct:updateMergedNetexStatus")
                .end()
                .routeId("tiamat-handle-global-netex-export-case");
    }

    private void countEvent(Boolean isPOI, Boolean isParkings, ExportJob exportJob) {
        if (exportJob == null || JobStatus.PROCESSING.equals(exportJob.getStatus())){
            return;
        }

        ExportType exportType;
        if (isPOI) {
            exportType = ExportType.POI;
        } else if (isParkings) {
            exportType = ExportType.PARKING;
        } else {
            exportType = ExportType.ARRET;
        }
        metrics.countExports(exportType,JobStatus.FINISHED.equals(exportJob.getStatus()) ? "OK" : exportJob.getStatus().name());
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


