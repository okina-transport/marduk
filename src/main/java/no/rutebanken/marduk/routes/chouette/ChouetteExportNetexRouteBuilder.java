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
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.EXPORT_FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.NO_GTFS_EXPORT;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USER;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

@Component
public class ChouetteExportNetexRouteBuilder extends AbstractChouetteRouteBuilder {
    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.export.days.forward:365}")
    private int daysForward;

    @Value("${chouette.export.days.back:365}")
    private int daysBack;

    @Value("${chouette.netex.export.stops:false}")
    private boolean exportStops;

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    @Autowired
    FileSystemService fileSystemService;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteExportNetexQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette Netex export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")

                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> {
                    String user = e.getIn().getHeader(USER, String.class);
                    e.getIn().setHeader(JSON_PART, Parameters.getNetexExportProvider(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportStops, user));
                }) //Using header to addToExchange json data
                .log(LoggingLevel.INFO, correlation() + "Creating multipart request")
                .process(e -> toGenericChouetteMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/netexprofile")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNetexExportResult"))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT_NETEX.name()))
                .removeHeader("loopCounter")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-start-export-netex");


        from("direct:processNetexExportResult")
                .choice()
                    .when(simple("${header.action_report_result} == 'OK'"))
                    .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")
                    .log(LoggingLevel.DEBUG, correlation() + "Calling url ${header.data_url}")
                    .removeHeaders("Camel*")
                    .setBody(simple(""))
                    .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                    .toD("${header.data_url}")
                    .process(e -> {
                        File file = fileSystemService.getOfferFile(e);
                        e.getIn().setHeader("fileName", file.getName());
                        e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
                    })
                    .process(exportToConsumersProcessor)
                    .choice()
                        .when(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.generateDatedServiceJourneyIds)
                        .to("direct:uploadDatedExport")
                    .end()
                    .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                    .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
//                    .setHeader(EXPORT_FILE_NAME, simple(Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
                    .to("direct:uploadBlobExport")
                    .process(e -> {
                        log.info("Upload to consumers and blob store completed");
                    })
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.OK).build())
                    .to("direct:updateStatus")
                    .removeHeader(Constants.CHOUETTE_JOB_ID)
                    .setBody(constant(null))
//                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_GRAPH).state(JobEvent.State.PENDING).build())
//                    .to("direct:exportMergedNetex")
                    .choice()
                        .when(e -> !e.getIn().getHeader(NO_GTFS_EXPORT, Boolean.class))
                            .to("activemq:queue:ChouetteExportGtfsQueue")
                        .end()
                    .endChoice()
                .endChoice()
                .when(simple("${header.action_report_result} == 'NOK'"))
                    .log(LoggingLevel.WARN, correlation() + "Netex export failed")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.FAILED).build())
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + "Something went wrong on Netex export")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.FAILED).build())
                .end()
                .to("direct:updateStatus")
                .removeHeader(Constants.CHOUETTE_JOB_ID)
                .routeId("chouette-process-export-netex-status");
    }
}
