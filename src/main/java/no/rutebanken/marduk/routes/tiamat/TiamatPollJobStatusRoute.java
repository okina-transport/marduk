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

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.Utils.PollJobStatusRoute;
import no.rutebanken.marduk.routes.chouette.*;
import no.rutebanken.marduk.routes.chouette.json.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.Status.*;

@Component
public class TiamatPollJobStatusRoute extends AbstractChouetteRouteBuilder {

    @Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    private int maxConsumers = 5;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Autowired
    UpdateExportTemplateProcessor updateExportTemplateProcessor;

    @Autowired
    CreateMail createMail;

    @Autowired
    PollJobStatusRoute pollJobStatusRoute;

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
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.CANCELLED).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to("direct:updateStatus")
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

        from("activemq:queue:TiamatPollStatusQueue?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .transacted()
                .validate(header(Constants.CORRELATION_ID).isNotNull())
                .validate(header(Constants.JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .validate(header(Constants.JOB_STATUS_URL).isNotNull())
                .validate(header(Constants.JOB_STATUS_JOB_TYPE).isNotNull())
                .to("direct:tiamatCheckJobStatus")
                .routeId("tiamat-validate-job-status-parameters");

        from("direct:tiamatCheckJobStatus")
                .process(e -> e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1))
                .setProperty(Constants.CHOUETTE_REFERENTIAL, header(Constants.CHOUETTE_REFERENTIAL))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setProperty("tiamat_url", header(Constants.JOB_STATUS_URL))
                .log(LoggingLevel.DEBUG, correlation() + "Calling Tiamat with URL: ${exchangeProperty.tiamat_url}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                // Attempt to retrigger delivery in case of errors
                .process(e -> e.getOut().setHeaders(e.getIn().getHeaders()))
                .toD("${exchangeProperty.tiamat_url}")
                .process(e -> e.getIn().setBody(new URL(e.getIn().getHeader(Constants.JOB_STATUS_URL, String.class).replace("http4", "http")).openConnection().getInputStream()))
                .choice()
                    .when(simple("${header.JOB_ID} != null"))
                        .process(e -> {
                            boolean isExportDone = false;
                            if(JobEvent.TimetableAction.IMPORT_NETEX.name().equals(e.getIn().getHeader(JOB_STATUS_JOB_TYPE))) {
                                String json = e.getIn().getBody(String.class);
                                JobResponse jobResponse = new ObjectMapper().readValue(json, JobResponse.class);
                                isExportDone = jobResponse.getStatus().isDone();
                                if (FINISHED == jobResponse.getStatus()) {
                                    e.getProperties().put("STATUS", "FINISHED");
                                    e.getIn().setHeader("action_report_result", "OK");
                                    e.getIn().setBody(json);
                                }
                            }
                            if (isExportDone) {
                                e.getIn().removeHeader(Constants.TIAMAT_STOP_PLACES_EXPORT);
                                e.getIn().removeHeader(Constants.TIAMAT_POINTS_OF_INTEREST_EXPORT);
                                e.getIn().removeHeader(Constants.TIAMAT_PARKINGS_EXPORT);
                            }
                        })
                        .choice()
                            .when(simple("${exchangeProperty.STATUS} == 'FINISHED'"))
                                .toD("${header." + Constants.JOB_STATUS_ROUTING_DESTINATION + "}")
                            .otherwise()
                                .to("direct:tiamatRescheduleJob")
                        .endChoice()
                        .stop()
                    .otherwise()
                        .unmarshal().json(JsonLibrary.Jackson, JobResponseWithLinks.class)
                .end()
                .setProperty("current_status", simple("${body.status}"))
                .choice()
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
                    .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "' || ${header.current_status} == '" + PROCESSING + "' || ${header.current_status} == '" + RESCHEDULED + "'"))
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
                    .when(simple("${header.current_status} == '" + CANCELED + "' || ${header.current_status} == '" + FAILED + "'"))
                        .log(LoggingLevel.WARN, correlation() + "Job ended in state CANCELLED. Stopping route.")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(JOB_STATUS_JOB_TYPE))).state(State.CANCELLED).build())
                        .to("direct:updateStatus")
                        .stop()
                .end()
                // Fetch and parse action report
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.OK).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to("direct:updateStatus")
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
}


