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

package no.rutebanken.marduk.routes.fares;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.Utils.ImportRouteBuilder;
import no.rutebanken.marduk.Utils.PollJobStatusRoute;
import no.rutebanken.marduk.routes.chouette.*;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.JobResponseWithLinks;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import no.rutebanken.marduk.security.TokenService;
import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.Status.*;

@Component
public class FaresPollJobStatusRoute extends AbstractChouetteRouteBuilder {

    @Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${fares.url}")
    private String faresUrl;

    private int maxConsumers = 5;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Autowired
    UpdateExportTemplateProcessor updateExportTemplateProcessor;

    @Autowired
    CreateMail createMail;

    @Autowired
    PollJobStatusRoute pollJobStatusRoute;

    @Autowired
    TokenService tokenService;

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

        from("direct:faresGetJobsForProvider")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for provider id '${header." + PROVIDER_ID + "}'")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                })
                .setProperty("fares_url", simple(faresUrl + "/${header." + CHOUETTE_REFERENTIAL + "}"))
                .to("direct:faresGetJobs")
                .routeId("fares-list-jobs-for-provider");

        from("direct:faresGetJobs")
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .process(exchange -> {
                    String url = (String) exchange.getProperty("fares_url");

                    // Convert camel dynamic endpoint format (http4:xxxx) to url (http://xxx) before manipulating url. Needed as interception
                    // does not seem to work with // in target anymore (as of camel 2.22.0)
                    boolean dynamicEndpointNotation=!url.contains("://");
                    if (dynamicEndpointNotation) {
                        url = url.replaceFirst(":", "://");
                    }
                    URIBuilder uriBuilder = new URIBuilder(url);
                    if (exchange.getIn().getHeader("action") != null) {
                        uriBuilder.addParameter("action", (String) exchange.getIn().getHeader("action"));
                    }
                    if (exchange.getIn().getHeader("status") != null) {

                        Object statusList = exchange.getIn().getHeader("status");
                        if (statusList instanceof List) {
                            for (String status : (List<String>) statusList) {
                                uriBuilder.addParameter("status", status);
                            }
                        } else {
                            uriBuilder.addParameter("status", (String) statusList);
                        }
                    }
                        uriBuilder.addParameter("addActionParameters", Boolean.FALSE.toString());
                    String newUri = uriBuilder.toString();
                    exchange.setProperty("fares_url", newUri);
                })
                .toD("${exchangeProperty.fares_url}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse[].class)
                .routeId("fares-list-jobs");


        from("direct:faresCancelJob")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.DELETE))
                .process(exchange -> {
                    String folder = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
                    String jobId = exchange.getIn().getHeader(JOB_ID, String.class);

                    if (jobId == null || folder == null) {
                        throw new IllegalArgumentException("Missing required data: jobId, folder");
                    }

                    exchange.getOut().setHeader("jobId", jobId);
                    exchange.getOut().setHeader("folder", folder);

                    if (exchange.getIn().getHeader("Authorization") == null) {
                        exchange.getOut().setHeader("Authorization", "Bearer " + tokenService.getToken());
                    }

                    String url = faresUrl + "/" + folder + "/scheduled_jobs" + jobId;
                    url = url.replace("http://", "http4://").replaceAll("([^:])//+", "$1/");

                    exchange.setProperty("fares_url", url);
                })
                .toD("${exchangeProperty.fares_url}")
                .setBody(constant(null))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.CANCELLED).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to("direct:updateStatus")
                .routeId("fares-cancel-job");

        from("direct:faresCancelAllJobsForProvider")
                .process(e -> e.getIn().setHeader("status", Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:faresGetJobsForProvider")
                .sort(body(), new JobResponseDescendingSorter())
                .removeHeaders("Camel*")
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(Constants.JOB_ID, simple("${body.id}"))
                .setBody(constant(null))
                .to("direct:faresCancelJob")
                .routeId("fares-cancel-all-jobs-for-provider");

        from("direct:faresCancelAllJobsForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(Constants.PROVIDER_ID, simple("${body.id}"))
                .setBody(constant(null))
                .removeHeaders("Camel*")
                .to("direct:faresCancelAllJobsForProvider")
                .routeId("fares-cancel-all-jobs-for-all-providers");

        from("jms:queue:FaresPollStatusQueue?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .transacted()
                .validate(header(Constants.CORRELATION_ID).isNotNull())
                .validate(header(Constants.JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .validate(header(Constants.JOB_STATUS_URL).isNotNull())
                .validate(header(Constants.JOB_STATUS_JOB_TYPE).isNotNull())
                .to("direct:faresCheckJobStatus")
                .routeId("fares-validate-job-status-parameters");

        from("direct:faresCheckJobStatus")
                .process(e -> e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1))
                .setProperty(CHOUETTE_REFERENTIAL, header(CHOUETTE_REFERENTIAL))
                .removeHeaders("Camel*")
                .setBody(constant(""))
//                .setProperty("fares_url", header(JOB_STATUS_URL))
                .log(LoggingLevel.DEBUG, correlation() + "Calling Fares with URL: ${exchangeProperty.fares_url}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                // Attempt to retrigger delivery in case of errors
                .process(exchange -> {
                    String folder = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
                    String jobId = exchange.getIn().getHeader(JOB_ID, String.class);

                    if (jobId == null || folder == null) {
                        throw new IllegalArgumentException("Missing required data: jobId, folder");
                    }

                    exchange.getOut().setHeader("jobId", jobId);
                    exchange.getOut().setHeader("folder", folder);

                    if (exchange.getIn().getHeader("Authorization") == null) {
                        exchange.getOut().setHeader("Authorization", "Bearer " + tokenService.getToken());
                    }

                    String url = faresUrl + "/" + folder + "/scheduled_jobs/" + jobId;
                    url = url.replace("http://", "http4://").replaceAll("([^:])//+", "$1/");

                    exchange.setProperty("fares_url", url);
                    exchange.setProperty("PROVIDER_ID", exchange.getIn().getHeader(PROVIDER_ID));
                    exchange.setProperty("CORRELATION_ID", exchange.getIn().getHeader(CORRELATION_ID));
                    exchange.setProperty("CHOUETTE_REFERENTIAL", exchange.getIn().getHeader(CHOUETTE_REFERENTIAL));
                })
                .toD("${exchangeProperty.fares_url}")
                .process(e -> {
                    e.getIn().setHeader(PROVIDER_ID, e.getProperty("PROVIDER_ID"));
                    e.getIn().setHeader(CORRELATION_ID, e.getProperty("CORRELATION_ID"));
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, e.getProperty("CHOUETTE_REFERENTIAL"));
                })
                .choice()
                    .when(simple("${header.RutebankenJobId} != null"))
                        .process(e -> {
                            boolean isExportDone = false;
                            if(TimetableAction.IMPORT_NETEX.name().equals(e.getIn().getHeader(JOB_STATUS_JOB_TYPE))) {
                                String json = e.getIn().getBody(String.class);
                                if (!json.equals("{}")) {
                                    JobResponse jobResponse = new ObjectMapper().readValue(json, JobResponse.class);
                                    isExportDone = jobResponse.getStatus().isDone();
                                    e.getProperties().put("STATUS", jobResponse.getStatus());
                                }
                            }
                            if (isExportDone) {
                                e.getIn().removeHeader(FARE_NETEX_EXPORT);
                            }
                        })
                        .choice()
                            .when(simple("${exchangeProperty.STATUS} == 'FINISHED' || ${exchangeProperty.STATUS} == 'FAILED'"))
                                .toD("${header." + JOB_STATUS_ROUTING_DESTINATION + "}")
                            .otherwise()
                                .to("direct:faresRescheduleJob")
                        .endChoice()
                        .stop()
                    .otherwise()
                        .unmarshal().json(JsonLibrary.Jackson, JobResponseWithLinks.class)
                .end()
                .setProperty("current_status", simple("${body.status}"))
                .choice()
                    .when(PredicateBuilder.or(simple("${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.STARTED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.RESCHEDULED}"),
                            simple("${header.loopCounter} > " + maxRetries)))
                    .to("direct:faresJobStatusDone")
                .otherwise()
                     // Update status
                    .to("direct:faresRescheduleJob")
                .end()
                .routeId("fares-get-job-status");

        from("direct:faresRescheduleJob")
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
                .to("jms:queue:FaresPollStatusQueue")
                .routeId("fares-reschedule-job");

        from("direct:faresJobStatusDone")
                .log(LoggingLevel.DEBUG, correlation() + "Exited retry loop with status ${header.current_status}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .inOnly("direct:faresHandleGlobalNetexExportCase")
                .choice()
                    .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "' || ${header.current_status} == '" + PROCESSING + "' || ${header.current_status} == '" + RESCHEDULED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(JOB_STATUS_JOB_TYPE))).state(State.TIMEOUT).build())
                        .to("direct:updateStatus")
                        .stop()
                    .when(simple("${header.current_status} == '" + ABORTED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state FAILED. Stopping route.")
                        .to("direct:updateStatus")
                        .stop()
                    .when(simple("${header.current_status} == '" + CANCELED + "' || ${header.current_status} == '" + FAILED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state CANCELLED. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(JOB_STATUS_JOB_TYPE))).state(State.CANCELLED).build())
                        .to("direct:updateStatus")
                        .stop()
                .end()
                // Fetch and parse action report
                .process(e -> {
                    JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.OK).type(e.getIn().getHeader(FILE_TYPE, String.class)).build();
                    if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                        createMail.createMail(e, "NETEX", ImportRouteBuilder.getTimeTableAction(e), true);
                    }
                })
                .to("direct:updateStatus")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .routeId("fares-process-job-reports");


        from("direct:faresHandleGlobalNetexExportCase")
                .choice()
                    .when(e-> BooleanUtils.isTrue((Boolean) e.getIn().getHeader(NETEX_EXPORT_GLOBAL)))
                        .inOnly("direct:updateMergedNetexStatus")
                .end()
                .routeId("fares-handle-global-netex-export-case");
    }
}


