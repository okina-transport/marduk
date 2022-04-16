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

import static no.rutebanken.marduk.Constants.*;
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
                    // Force new correlation ID : each export must have its own correlation ID to me displayed correctly in export screen
                    e.getIn().setHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString());
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                    String exportName = org.springframework.util.StringUtils.hasText(e.getIn().getHeader(EXPORTED_FILENAME, String.class)) && !e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class) ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : "offre";
                    e.getIn().setHeader(FILE_NAME, exportName);
                    e.getIn().setHeader(FILE_TYPE, "netex");
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")

                .process(e -> {
                    final Boolean isSimulation = e.getIn().getHeader(IS_SIMULATION_EXPORT, Boolean.class);
                    if (isSimulation != null && isSimulation) {
                        e.getIn().setHeader(CHOUETTE_REFERENTIAL, "simulation_" + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                        e.getIn().setHeader(OKINA_REFERENTIAL, "simulation_" + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                    } else {
                        e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                        e.getIn().setHeader(OKINA_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                    }
                })
                .process(e -> {
                    String user = e.getIn().getHeader(USER, String.class);
                    String exportedFilename = e.getIn().getHeader(EXPORTED_FILENAME) != null && !e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class) ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : null;
                    e.getIn().setHeader(JSON_PART, Parameters.getNetexExportProvider(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportStops, user, exportedFilename));
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
                    String fileName = file != null ? file.getName() : "defaultFileName";
                    e.getIn().setHeader("fileName", fileName);
                    e.getIn().setHeader(EXPORT_FILE_NAME,fileName);
                })
                .choice()
                    .when(e -> e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class))
                        .setHeader(FILE_HANDLE, simple(MERGED_NETEX_ROOT_DIR+"/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
                        .to("direct:uploadBlob")
                        .to("direct:updateStatus")
                    .otherwise()
                        .process(exportToConsumersProcessor)
                 //   .to("direct:exportMergedNetex")
                .endChoice()
                .choice()
                .when(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.generateDatedServiceJourneyIds)
                .endChoice()
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .process(e -> {
                    log.info("Upload to consumers and blob store completed");
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .removeHeader(Constants.CHOUETTE_JOB_ID)
                .setBody(constant(null))
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

        from("direct:chouetteNetexExportForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getMobiitiProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(PROVIDER_ID, simple("${body.id}"))
                .setBody(constant(null))
                .inOnly("activemq:queue:ChouetteExportNetexQueue")
                .routeId("chouette-netex-export-all-providers");
    }
}
