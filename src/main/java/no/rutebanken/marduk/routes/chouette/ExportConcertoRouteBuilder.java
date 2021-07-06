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

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.config.SchedulerConcertoConfig;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.json.JSONObject;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CONCERTO_EXPORT_SCHEDULER;
import static no.rutebanken.marduk.Constants.EXPORT_END_DATE;
import static no.rutebanken.marduk.Constants.EXPORT_FILE_NAME;
import static no.rutebanken.marduk.Constants.EXPORT_START_DATE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TIME_ZONE;
import static no.rutebanken.marduk.Constants.USER;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Exports concerto files from Chouette
 */
@Component
public class ExportConcertoRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.export.days.forward:365}")
    private int daysForward;

    @Value("${chouette.export.days.back:365}")
    private int daysBack;

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    SchedulerConcertoConfig schedulerConcerto;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteExportConcertoQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette Concerto export for provider with id ${header." + PROVIDER_ID + "}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(e -> {
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT_CONCERTO).state(State.PENDING).build())
                .to("direct:updateStatus")
                .process(e -> {
                    String user = e.getIn().getHeader(USER, String.class);
                    Date startDate = null;
                    Date endDate = null;

                    if (e.getIn().getHeader(EXPORT_START_DATE) != null && e.getIn().getHeader(EXPORT_END_DATE) != null) {
                        Long start = e.getIn().getHeader(EXPORT_START_DATE) != null ? (Long) e.getIn().getHeader(EXPORT_START_DATE, Long.class) : null;
                        Long end = e.getIn().getHeader(EXPORT_END_DATE) != null ? (Long) e.getIn().getHeader(EXPORT_END_DATE, Long.class) : null;
                        startDate = (start != null) ? new Date(start) : null;
                        endDate = (end != null) ? new Date(end) : null;
                    }

                    e.getIn().setHeader(JSON_PART, Parameters.getConcertoExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), user, startDate, endDate));
                }) //Using header to addToExchange json data
                .log(LoggingLevel.INFO, correlation() + "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> toGenericChouetteMultipart(e))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/admin/exporter/concerto")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processExportConcertoResult"))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(TimetableAction.EXPORT_CONCERTO.name()))
                .removeHeader("loopCounter")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-export-concerto-job");


        from("direct:processExportConcertoResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
                .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")
                .log(LoggingLevel.INFO, correlation() + "Calling url ${header.data_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${header.data_url}")
                .process(e -> {
                    File file = fileSystemService.getOfferFileConcerto(e);
                    e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
                    e.getIn().setBody(new FileInputStream(file));
                })
                .process(exportToConsumersProcessor)
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .to("direct:uploadBlobExport")
                .log(LoggingLevel.INFO, "Upload to consumers and blob store completed")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT_CONCERTO).state(State.OK).build())
                .when(simple("${header.action_report_result} == 'NOK'"))
                .log(LoggingLevel.WARN, correlation() + "Export failed")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT_CONCERTO).state(State.FAILED).build())
                .otherwise()
                .log(LoggingLevel.ERROR, correlation() + "Something went wrong on export")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT_CONCERTO).state(State.FAILED).build())
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-export-concerto-status");


        from("direct:updateSchedulerForConcertoExport")
                .log(LoggingLevel.INFO, getClass().getName(), "Update scheduler Concerto Export")
                .process(this::updateSchedulerForConcertoExport)
                .routeId("update-scheduler-process-concerto-export");

        from("direct:getCron")
                .log(LoggingLevel.INFO, getClass().getName(), "Get scheduler Concerto Export")
                .process(this::getCron)
                .routeId("get-cron-scheduler-process-concerto-export");
    }

    private void getCron(Exchange exchange) throws SchedulerException {
        SchedulerFactoryBean scheduler = schedulerConcerto.getSchedulerConcerto();
        CronTrigger trigger = (CronTrigger) scheduler.getScheduler().getTrigger(TriggerKey.triggerKey("ConcertoJobTrigger"));
        if (trigger != null) {
            String[] dateFromCron = trigger.getCronExpression().split(" ");
            String frequency = dateFromCron[3].split("/")[1];
            JSONObject jsonObject = new JSONObject();
            jsonObject.append("date", trigger.getStartTime().getTime());
            jsonObject.append("frequency", frequency);
            exchange.getIn().setBody(jsonObject.toString());
        }
    }

    private void updateSchedulerForConcertoExport(Exchange e) throws SchedulerException, ParseException {
        Map headers = (Map) e.getIn().getBody(Map.class).get("headers");
        if (headers != null) {
            if (headers.get(CONCERTO_EXPORT_SCHEDULER) != null) {
                SchedulerFactoryBean scheduler = schedulerConcerto.getSchedulerConcerto();

                String concertoExportSchedulerCron = (String) headers.get(CONCERTO_EXPORT_SCHEDULER);

                JobDetailFactoryBean concertoJobDetails = schedulerConcerto.getJobConcerto();

                String[] dateFromCron = concertoExportSchedulerCron.split(" ");
                String format = "yyyy-MM-dd HH:mm";
                SimpleDateFormat simpleStartDateFormat = new SimpleDateFormat(format);
                Date startDate = simpleStartDateFormat.parse(dateFromCron[6].split("-")[0] + "-" + dateFromCron[4] + "-" + dateFromCron[3].split("/")[0] + " " + dateFromCron[2] + ":" + dateFromCron[1]);

                concertoExportSchedulerCron = dateFromCron[0] + " " + dateFromCron[1] + " " + dateFromCron[2] + " " + dateFromCron[3] + " * " + dateFromCron[5] + " " + dateFromCron[6];

                Trigger concertoTrigger = TriggerBuilder.newTrigger().forJob(concertoJobDetails.getObject())
                        .withIdentity("ConcertoJobTrigger")
                        .withSchedule(CronScheduleBuilder.cronSchedule(concertoExportSchedulerCron))
                        .startAt(startDate)
                        .build();

                scheduler.start();

                if (scheduler.getScheduler().checkExists(concertoJobDetails.getObject().getKey())) {
                    scheduler.getScheduler().deleteJob(concertoJobDetails.getObject().getKey());
                }

                scheduler.getScheduler().scheduleJob(concertoJobDetails.getObject(), concertoTrigger);

                log.info("Concerto Export Scheduler created with cron expression: " + concertoExportSchedulerCron);
            }
        }
    }
}


