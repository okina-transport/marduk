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
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.EXPORTED_FILENAME;
import static no.rutebanken.marduk.Constants.EXPORT_END_DATE;
import static no.rutebanken.marduk.Constants.EXPORT_LINES_IDS;
import static no.rutebanken.marduk.Constants.EXPORT_NAME;
import static no.rutebanken.marduk.Constants.EXPORT_START_DATE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USER;
import static no.rutebanken.marduk.Constants.WORKLOW;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Exports neptune files from Chouette
 */
@Component
public class ChouetteExportNeptuneRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Autowired
    UpdateExportTemplateProcessor updateExportTemplateProcessor;

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    CreateMail createMail;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteExportNeptuneQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette NEPTUNE export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    // Force new correlation ID : each export must have its own correlation ID to me displayed correctly in export screen
                    e.getIn().setHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString());
                    String exportName = org.springframework.util.StringUtils.hasText(e.getIn().getHeader(EXPORTED_FILENAME, String.class)) ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : "offre";
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                    e.getIn().setHeader(FILE_NAME, exportName);
                    e.getIn().setHeader(FILE_TYPE, "neptune");
                    log.info("Lancement export Neptune - Fichier : " + exportName + " - Espace de données : " + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.PENDING).build())
                .to("direct:updateStatus")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> {
                    String user = e.getIn().getHeader(USER, String.class);
                    String neptuneParams;

                    Date startDate = null;
                    Date endDate = null;
                    String exportName = e.getIn().getHeader(EXPORT_NAME) != null ? (String) e.getIn().getHeader(EXPORT_NAME) : null;
                    String exportedFilename = e.getIn().getHeader(EXPORTED_FILENAME) != null ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : null;


                    if (e.getIn().getHeader(EXPORT_START_DATE) != null && e.getIn().getHeader(EXPORT_END_DATE) != null) {
                        Long start = e.getIn().getHeader(EXPORT_START_DATE) != null ? e.getIn().getHeader(EXPORT_START_DATE, Long.class) : null;
                        Long end = e.getIn().getHeader(EXPORT_END_DATE) != null ? e.getIn().getHeader(EXPORT_END_DATE, Long.class) : null;
                        startDate = (start != null) ? new Date(start) : null;
                        endDate = (end != null) ? new Date(end) : null;
                    }


                    if (e.getIn().getHeader(EXPORT_LINES_IDS) == null) {
                        neptuneParams = Parameters.getNeptuneExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportName, user, null, startDate, endDate, exportedFilename);
                    } else {
                        String linesIdsS = e.getIn().getHeader(EXPORT_LINES_IDS, String.class);
                        List<Long> linesIds = Arrays.stream(StringUtils.split(linesIdsS, ",")).map(s -> Long.valueOf(s)).collect(toList());
                        neptuneParams = Parameters.getNeptuneExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportName, user, linesIds, startDate, endDate, exportedFilename);
                    }


                    e.getIn().setHeader(JSON_PART, neptuneParams);
                }) //Using header to addToExchange json data
                .log(LoggingLevel.INFO, correlation() + "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(this::toGenericChouetteMultipart)
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/neptune")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNeptuneExportResult"))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(TimetableAction.EXPORT.name()))
                .removeHeader("loopCounter")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-export-neptune-job");


        from("direct:processNeptuneExportResult")
                .log(LoggingLevel.INFO, getClass().getName())
                .choice()
                    .when(simple("${header.action_report_result} == 'OK'"))
                        .log(LoggingLevel.INFO,"Export Neptune terminé - Fichier : ${header." + FILE_NAME + "} - Espace de données : ${header." + CHOUETTE_REFERENTIAL + "}")
                        .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")
                        .log(LoggingLevel.INFO, correlation() + "Calling url ${header.data_url}")
                        .removeHeaders("Camel*")
                        .setBody(simple(""))
                        .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                        .process(exportToConsumersProcessor)
                        .to("direct:updateExportToConsumerStatus")
                        .process(updateExportTemplateProcessor)
                .log(LoggingLevel.INFO, "Upload to consumers and blob store completed")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(State.OK).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, "NEPTUNE", JobEvent.TimetableAction.EXPORT, true);
                            }
                        })
                    .when(simple("${header.action_report_result} == 'NOK'"))
                        .log(LoggingLevel.WARN, correlation() + "Export failed")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, "NEPTUNE", JobEvent.TimetableAction.EXPORT, false);
                            }
                        })
                    .otherwise()
                        .log(LoggingLevel.ERROR, correlation() + "Something went wrong on export")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, "NEPTUNE", JobEvent.TimetableAction.EXPORT, false);
                            }
                        })
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-export-neptune-status");

    }

}


