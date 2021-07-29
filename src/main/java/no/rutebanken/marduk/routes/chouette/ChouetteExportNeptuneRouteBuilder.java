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

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.EXPORTED_FILENAME;
import static no.rutebanken.marduk.Constants.EXPORT_END_DATE;
import static no.rutebanken.marduk.Constants.EXPORT_FILE_NAME;
import static no.rutebanken.marduk.Constants.EXPORT_LINES_IDS;
import static no.rutebanken.marduk.Constants.EXPORT_NAME;
import static no.rutebanken.marduk.Constants.EXPORT_START_DATE;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USER;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Exports neptune files from Chouette
 */
@Component
public class ChouetteExportNeptuneRouteBuilder extends AbstractChouetteRouteBuilder {

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
                .process(e -> toGenericChouetteMultipart(e))
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
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    log.info(getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");
                })
                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
                .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")
                .log(LoggingLevel.INFO, correlation() + "Calling url ${header.data_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .process(e -> {
                    log.info("Starting export download");
                })
                .toD("${header.data_url}")
                .process(e -> {
                    File file = fileSystemService.getOfferFile(e);
                    e.getIn().setHeader("fileName", file.getName());
                    e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
                })
                .setHeader("fileName", simple("NEPTUNE.zip"))
                .process(exportToConsumersProcessor)
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "neptune/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NEPTUNE_FILENAME))

                .process(e -> {
                    log.info("Starting neptune export upload");
                    e.getIn().setBody(fileSystemService.getOfferFile(e));
                })
                .to("direct:uploadBlobExport")
                .process(e -> {
                    log.info("Upload to consumers and blob store completed");
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.OK).build())
                .when(simple("${header.action_report_result} == 'NOK'"))
                .log(LoggingLevel.WARN, correlation() + "Export failed")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.FAILED).build())
                .otherwise()
                .log(LoggingLevel.ERROR, correlation() + "Something went wrong on export")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.FAILED).build())
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-export-neptune-status");

    }

}


