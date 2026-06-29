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

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.UserActionsLoggingService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.Utils.getLastPathElementOfUrl;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;

@Component
public class ChouetteExportNetexRouteBuilder extends AbstractChouetteRouteBuilder {

    private final String chouetteUrl;
    private final boolean exportStops;
    private final boolean publicPublication;
    private final ExportToConsumersProcessor exportToConsumersProcessor;
    private final UpdateExportTemplateProcessor updateExportTemplateProcessor;
    private final CreateMail createMail;
    private final String lugUrl;
    private final UserActionsLoggingService userActionsLoggingService;

    public ChouetteExportNetexRouteBuilder(@Value("${chouette.url}") String chouetteUrl, @Value("${chouette.netex.export.stops:false}") boolean exportStops, @Value("${google.publish.public:false}") boolean publicPublication,
                                           ExportToConsumersProcessor exportToConsumersProcessor, UpdateExportTemplateProcessor updateExportTemplateProcessor, CreateMail createMail,
                                           @Value("${lug.url}")String lugUrl, UserActionsLoggingService userActionsLoggingService) {
        this.chouetteUrl = chouetteUrl;
        this.exportStops = exportStops;
        this.publicPublication = publicPublication;
        this.exportToConsumersProcessor = exportToConsumersProcessor;
        this.updateExportTemplateProcessor = updateExportTemplateProcessor;
        this.createMail = createMail;
        this.lugUrl = lugUrl;
        this.userActionsLoggingService = userActionsLoggingService;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("jms:queue:ChouetteExportNetexQueue?transacted=true").streamCache(Boolean.TRUE)
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette Netex export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    // Force new correlation ID : each export must have its own correlation ID to me displayed correctly in export screen
                    e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString());
                    e.getIn().removeHeader(JOB_ID);
                    String exportName = e.getIn().getHeader(EXPORT_FILE_NAME) != null ? e.getIn().getHeader(EXPORT_FILE_NAME, String.class) :
                            org.springframework.util.StringUtils.hasText(e.getIn().getHeader(EXPORTED_FILENAME, String.class)) && !e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class) ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : "offre";
                    e.getIn().setHeader(FILE_NAME, exportName);
                    e.getIn().setHeader(FILE_TYPE, "netex");
                    log.info("Lancement export Netex - Fichier : " + exportName + " - Espace de données : " + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.PENDING).build())
                .to(ROUTE_UPDATE_STATUS)

                .process(e -> {
                    final Boolean isSimulation = e.getIn().getHeader(IS_SIMULATION_EXPORT, Boolean.class);
                    if (isSimulation != null && isSimulation) {
                        e.getIn().setHeader(CHOUETTE_REFERENTIAL, "simulation_" + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                        e.getIn().setHeader(OKINA_REFERENTIAL, "simulation_" + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                        if (e.getIn().getHeader(EXPORT_SIMULATION_NAME) != null) {
                            e.getIn().setHeader(EXPORTED_FILENAME, e.getIn().getHeader(EXPORT_SIMULATION_NAME));
                        }
                    } else {
                        e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                        e.getIn().setHeader(OKINA_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                    }
                })
                .process(e -> {
                    String user = e.getIn().getHeader(USER, String.class);
                    String exportedFilename = e.getIn().getHeader(EXPORTED_FILENAME) != null && !e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class) ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : null;
                    String exportGeneratedMissingQuays = e.getIn().getHeader(EXPORT_GENERATED_MISSING_QUAYS) != null ? String.valueOf(e.getIn().getHeader(EXPORT_GENERATED_MISSING_QUAYS)) : null;
                    String exportExternalIds = e.getIn().getHeader(EXPORT_EXTERNAL_IDS) != null ? String.valueOf(e.getIn().getHeader(EXPORT_EXTERNAL_IDS)) : null;
                    e.getIn().setHeader(JSON_PART, Parameters.getNetexExportProvider(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportStops, user, exportedFilename, null, null, exportGeneratedMissingQuays, exportExternalIds));
                }) //Using header to addToExchange json data
                .bean(userActionsLoggingService,"recordUserAction")
                .log(LoggingLevel.INFO, correlation() + "Creating multipart request")
                .process(this::toGenericChouetteMultipart)
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/netexprofile")
                .process(e -> {
                    e.getIn().setHeader(JOB_STATUS_URL, e.getIn().getHeader("Location").toString());
                    e.getIn().setHeader(JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNetexExportResult"))
                .setHeader(JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT_NETEX.name()))
                .removeHeader("loopCounter")
                .to("jms:queue:ChouettePollStatusQueue")
                .routeId("chouette-start-export-netex");


        from("direct:processNetexExportResult")
                .process(e->{
                    if (e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class) != null &&
                            e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class).equals(true)) {
                        // For global Netex exports, result is forced to OK. A failure in one organization should not cause a global failure.
                        e.getIn().setHeader("action_report_result", "OK");
                    }
                })
                .log(LoggingLevel.INFO, "Export Netex terminé - Fichier : ${header." + FILE_NAME + "} - Espace de données : ${header." + CHOUETTE_REFERENTIAL + "}")
                .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")

                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
                        .to("direct:processNetexExportResultCompletedSuccessFully")
                .when(simple("${header.action_report_result} == 'NOK'"))
                        .to("direct:processNetexExportResultNOKstatus")
                .otherwise()
                        .to("direct:processNetexExportResultUndefinedStatus")
                .end()

                .removeHeader(JOB_ID)
                .routeId("chouette-process-export-netex-status");


        from("direct:processNetexExportResultUndefinedStatus")
                .log(LoggingLevel.ERROR, correlation() + "Something went wrong on Netex export")
                .process(e -> {
                    JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.FAILED).build();
                    if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                        createMail.createMail(e, "NETEX", JobEvent.TimetableAction.EXPORT_NETEX, false);
                    }
                })
                .to(ROUTE_UPDATE_STATUS)
                .routeId("chouette-process-export-netex-undefined-status");



        from("direct:processNetexExportResultNOKstatus")
                .log(LoggingLevel.WARN, correlation() + "Netex export failed")
                .process(e -> {
                    JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.FAILED).build();
                    if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                        createMail.createMail(e, "NETEX", JobEvent.TimetableAction.EXPORT_NETEX, false);
                    }
                })
                .to(ROUTE_UPDATE_STATUS)
                .routeId("chouette-process-export-netex-nok-status");

        from("direct:processNetexExportResultCompletedSuccessFully")
                .choice()
                .when( simple("${header." + POST_PROCESS + "} != null"))
                    .to("direct:sendToLUG")
                .otherwise()
                    .to("direct:processNetexExportResultEnd")
                .endChoice()
                .routeId("chouette-process-export-netex-completed-successfully");

        from("direct:processNetexExportResultEnd")
                .log(LoggingLevel.DEBUG, correlation() + "Calling url ${header.data_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .choice()
                .when(e -> e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class))
                    .toD("${header.data_url}")
                    .setHeader(FILE_HANDLE, simple(MERGED_NETEX_ROOT_DIR + "/${header." + CHOUETTE_REFERENTIAL + "}-" + CURRENT_AGGREGATED_NETEX_FILENAME))

                .end()
                    .process(exportToConsumersProcessor)
                    .to("direct:updateExportToConsumerStatus")
                    .log(LoggingLevel.INFO, "Upload to consumers and blob store completed")
                    .process(updateExportTemplateProcessor)
                    .process(e -> {
                        JobEvent.TimetableAction action;
                        if (e.getIn().getHeader(NETEX_EXPORT_GLOBAL, Boolean.class)){
                            action = JobEvent.TimetableAction.EXPORT_NETEX_MERGED;
                        }else{
                            action = JobEvent.TimetableAction.EXPORT_NETEX;
                        }

                        JobEvent.providerJobBuilder(e).timetableAction(action).state(JobEvent.State.OK).build();
                        if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                            createMail.createMail(e, "NETEX", action, true);
                        }
                    })

                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .to(ROUTE_UPDATE_STATUS)
                .removeHeader(JOB_ID)
                .setBody(constant((Object) null))
                .choice()
                .when(e -> !e.getIn().getHeader(NO_GTFS_EXPORT, Boolean.class))
                    .to("jms:queue:ChouetteExportGtfsQueue")
                .end()
                .routeId("chouette-process-export-netex-status-end");


        from("direct:chouetteNetexExportForAllProviders")
                .process(e -> {
                    String allReferentialsNames = null;
                    if (e.getIn().getHeader(EXPORT_REFERENTIALS_NAMES) != null && StringUtils.isNotEmpty((String) e.getIn().getHeader(EXPORT_REFERENTIALS_NAMES))) {
                        allReferentialsNames = e.getIn().getHeader(EXPORT_REFERENTIALS_NAMES, String.class);
                        List<String> referentialsNames = Arrays.stream(StringUtils.split(allReferentialsNames, ",")).map(s -> "mobiiti_" + s).collect(toList());
                        log.info("Netex export global with mobi_iti providers => " + referentialsNames);
                        Collection<Provider> mobiitiProviders = getProviderRepository().getMobiitiProviders().stream().filter(provider -> referentialsNames.contains(provider.name)).collect(toList());
                        e.getIn().setBody(mobiitiProviders);
                    } else {
                        log.info("Netex export global with all mobi_iti providers");
                        e.getIn().setBody(getProviderRepository().getMobiitiProviders());
                        allReferentialsNames = getProviderRepository().getMobiitiProviders().stream().map(prov-> prov.getName().replace("mobiiti_","")).collect(Collectors.joining(","));
                    }

                    String correlationId = UUID.randomUUID().toString();
                    e.getIn().setHeader(CORRELATION_ID, correlationId);
                    e.getIn().removeHeader(JOB_ID);
                    String exportName = org.springframework.util.StringUtils.hasText(e.getIn().getHeader(EXPORTED_FILENAME, String.class)) ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : "offre";
                    e.getIn().setHeader(FILE_NAME, exportName);
                    e.getIn().setHeader(FILE_TYPE, "netex");
                    String user = e.getIn().getHeader(USER, String.class);
                    Long exportConfigurationId = e.getIn().getHeader(EXPORT_CONFIGURATION_ID) != null ? Long.valueOf((String) e.getIn().getHeader(EXPORT_CONFIGURATION_ID)) : null;
                    String exportedFilename = e.getIn().getHeader(EXPORTED_FILENAME) != null ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : null;
                    String exportGeneratedMissingQuays = e.getIn().getHeader(EXPORT_GENERATED_MISSING_QUAYS) != null ? String.valueOf(e.getIn().getHeader(EXPORT_GENERATED_MISSING_QUAYS)) : null;
                    String exportExternalIds = e.getIn().getHeader(EXPORT_EXTERNAL_IDS) != null ? String.valueOf(e.getIn().getHeader(EXPORT_EXTERNAL_IDS)) : null;
                    e.getIn().setHeader(JSON_PART, Parameters.getNetexExportProvider(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportStops, user, exportedFilename, allReferentialsNames, exportConfigurationId, exportGeneratedMissingQuays, exportExternalIds));
                    JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_NETEX_MERGED").fileName(exportedFilename).state(JobEvent.State.PENDING).type("netex").correlationId(correlationId).build();
                })
                .to(ROUTE_UPDATE_STATUS)
                .process(this::toGenericChouetteMultipart)
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/globalExport/netexprofile")
                .process(e -> {
                    e.getIn().setHeader(JOB_STATUS_URL, e.getIn().getHeader("Location").toString());
                    e.getIn().setHeader(JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setBody(constant((Object) null))
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNetexExportResult"))
                .setHeader(JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT_NETEX_MERGED.name()))
                .to("jms:queue:ChouettePollStatusQueue")
                .routeId("chouette-netex-export-all-providers");
    }
}
