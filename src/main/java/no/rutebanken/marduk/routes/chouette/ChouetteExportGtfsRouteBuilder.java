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
import no.rutebanken.marduk.domain.AttributionsExportModes;
import no.rutebanken.marduk.domain.IdFormat;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.IdParameters;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
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
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Exports gtfs files from Chouette
 */
@Component
public class ChouetteExportGtfsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

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

        from("activemq:queue:ChouetteExportGtfsQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette GTFS export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    // Force new correlation ID : each export must have its own correlation ID to me displayed correctly in export screen
                    e.getIn().setHeader(Constants.CORRELATION_ID,  UUID.randomUUID().toString());
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                    String exportName = "gtfs";
                    if(!e.getIn().getHeader(GTFS_EXPORT_GLOBAL, Boolean.class)){
                        exportName = org.springframework.util.StringUtils.hasText(e.getIn().getHeader(EXPORTED_FILENAME, String.class)) ?
                                (String) e.getIn().getHeader(EXPORTED_FILENAME) :
                                e.getIn().getHeader(EXPORT_NAME, String.class).replace(" ","_");
                    }
                    e.getIn().setHeader(FILE_NAME, exportName);
                    e.getIn().setHeader(FILE_TYPE, "gtfs");
                    log.info("Lancement export GTFS - Fichier : " + exportName + " - Espace de données : " + getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.PENDING).build())
                .to("direct:updateStatus")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(OKINA_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> {
                    String user = e.getIn().getHeader(USER, String.class);
                    String gtfsParams;

                    Date startDate = null;
                    Date endDate = null;
                    String exportName = e.getIn().getHeader(EXPORT_NAME) != null ? (String) e.getIn().getHeader(EXPORT_NAME) : null;
                    String stopIdPrefix = e.getIn().getHeader(STOP_ID_PREFIX) != null ? (String) e.getIn().getHeader(STOP_ID_PREFIX) : null;
                    IdFormat idFormat = e.getIn().getHeader(ID_FORMAT) != null ? IdFormat.valueOf((String)e.getIn().getHeader(ID_FORMAT)) : null;
                    String idSuffix = e.getIn().getHeader(ID_SUFFIX) != null ? (String) e.getIn().getHeader(ID_SUFFIX) : null;
                    String linePrefix = e.getIn().getHeader(LINE_ID_PREFIX) != null ? (String) e.getIn().getHeader(LINE_ID_PREFIX) : null;
                    String commercialPointIdPrefix = e.getIn().getHeader(COMMERCIAL_POINT_ID_PREFIX) != null ? (String) e.getIn().getHeader(COMMERCIAL_POINT_ID_PREFIX) : null;
                    Boolean commercialPointExport = e.getIn().getHeader(COMMERCIAL_POINT_EXPORT) != null ? (Boolean) e.getIn().getHeader(COMMERCIAL_POINT_EXPORT) : null;
                    Boolean googleMapsCompatibility = e.getIn().getHeader(GOOGLE_MAPS_COMPATIBILITY) != null ? (Boolean) e.getIn().getHeader(GOOGLE_MAPS_COMPATIBILITY) : null;
                    IdParameters idParams = new IdParameters(stopIdPrefix,idFormat,idSuffix,linePrefix,commercialPointIdPrefix);
                    AttributionsExportModes attributionsExportModes = e.getIn().getHeader(EXPORT_ATTRIBUTIONS) != null ? AttributionsExportModes.valueOf((String) e.getIn().getHeader(EXPORT_ATTRIBUTIONS)) : AttributionsExportModes.NONE;
                    String exportedFilename = "gtfs.zip";;
                    if(!e.getIn().getHeader(GTFS_EXPORT_GLOBAL, Boolean.class)){
                        exportedFilename = e.getIn().getHeader(EXPORTED_FILENAME) != null ? (String) e.getIn().getHeader(EXPORTED_FILENAME) : exportName.replace(" ","_") + ".zip";
                    }


                    if(e.getIn().getHeader(EXPORT_START_DATE) != null && e.getIn().getHeader(EXPORT_END_DATE) != null){
                        Long start = e.getIn().getHeader(EXPORT_START_DATE) != null ? e.getIn().getHeader(EXPORT_START_DATE, Long.class) : null;
                        Long end = e.getIn().getHeader(EXPORT_END_DATE) != null ? e.getIn().getHeader(EXPORT_END_DATE, Long.class) : null;
                        startDate = (start != null) ? new Date(start) : null;
                        endDate = (end != null) ? new Date(end) : null;
                    }

                    boolean mappingLinesIds = false;
                    if (e.getIn().getHeader(MAPPING_LINES_IDS) != null){
                        mappingLinesIds = (boolean) e.getIn().getHeader(MAPPING_LINES_IDS);
                    }

                    boolean keepOriginalId = true;
                    if (e.getIn().getHeader(KEEP_ORIGINAL_ID) != null){
                        keepOriginalId = (boolean) e.getIn().getHeader(KEEP_ORIGINAL_ID);
                    }


                    if (e.getIn().getHeader(EXPORT_LINES_IDS) == null && startDate != null && endDate != null) {
                        gtfsParams = Parameters.getGtfsExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportName, user, keepOriginalId, null, startDate, endDate, exportedFilename, idParams, mappingLinesIds, commercialPointExport, attributionsExportModes, googleMapsCompatibility);
                    } else if (e.getIn().getHeader(EXPORT_LINES_IDS) != null) {
                        String linesIdsS = e.getIn().getHeader(EXPORT_LINES_IDS, String.class);
                        List<Long> linesIds = Arrays.stream(StringUtils.split(linesIdsS, ",")).map(Long::valueOf).collect(toList());
                        gtfsParams = Parameters.getGtfsExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportName, user, keepOriginalId, linesIds, startDate, endDate, exportedFilename, idParams, mappingLinesIds, commercialPointExport, attributionsExportModes, googleMapsCompatibility);
                    } else {
                        gtfsParams = Parameters.getGtfsExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), user, keepOriginalId, exportedFilename, commercialPointExport, attributionsExportModes, googleMapsCompatibility);
                    }

                    e.getIn().setHeader(JSON_PART, gtfsParams);
                }) //Using header to addToExchange json data
                .log(LoggingLevel.INFO, correlation() + "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(this::toGenericChouetteMultipart)
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/gtfs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processExportResult"))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT.name()))
                .removeHeader("loopCounter")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-export-job");


        from("direct:processExportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                    .when(simple("${header.action_report_result} == 'OK'"))
                        .log(LoggingLevel.INFO,"Export GTFS terminé - Fichier : ${header." + FILE_NAME + "} - Espace de données : ${header." + CHOUETTE_REFERENTIAL + "}")
                        .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")
                        .log(LoggingLevel.INFO, correlation() + "Calling url ${header.data_url}")
                        .removeHeaders("Camel*")
                        .setBody(simple(""))
                        .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                        .choice()
                            .when(e -> e.getIn().getHeader(GTFS_EXPORT_GLOBAL, Boolean.class))
                                .toD("${header.data_url}")
                                .setHeader(FILE_HANDLE, simple("mobiiti_technique/gtfs/allFiles/${header.ID_FORMAT}/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_GTFS_FILENAME))
                                .to("direct:uploadBlob")
                                .to("direct:exportMergedGtfs")
                        .endChoice()
                        .process(exportToConsumersProcessor)
                        .to("direct:updateExportToConsumerStatus")
                        .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                        .log(LoggingLevel.INFO,"Upload to consumers and blob store completed")
                        .process(updateExportTemplateProcessor)
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.OK).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, "GTFS", JobEvent.TimetableAction.EXPORT, true);
                            }
                        })
                    .when(simple("${header.action_report_result} == 'NOK'"))
                        .log(LoggingLevel.WARN, correlation() + "Export failed")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, "GTFS", JobEvent.TimetableAction.EXPORT, false);
                            }
                        })
                    .otherwise()
                        .log(LoggingLevel.ERROR, correlation() + "Something went wrong on export")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, "GTFS", JobEvent.TimetableAction.EXPORT, false);
                            }
                        })
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-export-status");

        from("direct:addGtfsFeedInfo")
                .log(LoggingLevel.INFO, correlation() + "Adding feed_info.txt to GTFS file")
                .process(e -> {
                    // Add feed info
                    String feedInfoContent = "feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version,feed_contact_email,feed_contact_url\n";

                    feedInfoContent += "MOBIITI_A_CORRIGER" + ",";                     // feed_publisher_name
                    feedInfoContent += "https://www.okina.fr/#A_CORRIGER" + ",";   // feed_publisher_url
                    feedInfoContent += "fr-FR" + ",";                                 // feed_lang
                    feedInfoContent += ",";   // feed_start_date
                    feedInfoContent += ",";   // feed_end_date
                    feedInfoContent += ",";   // feed_version
                    feedInfoContent += ",";   // feed_contact_email
                    feedInfoContent += ",";   // feed_contact_url

                    File tmpFolder = new File(System.getProperty("java.io.tmpdir"));
                    File tmpFolder2 = new File(tmpFolder, UUID.randomUUID().toString());
                    tmpFolder2.mkdirs();
                    File feedInfoFile = new File(tmpFolder2, "feed_info.txt");

                    PrintWriter writer = new PrintWriter(feedInfoFile);
                    writer.write(feedInfoContent);
                    writer.close();


                    e.getIn().setBody(ZipFileUtils.addFilesToZip(e.getIn().getBody(InputStream.class), new File[]{feedInfoFile}));

                    feedInfoFile.delete();
                    tmpFolder2.delete();
                })
                .routeId("chouette-process-export-gtfs-feedinfo");

        from("direct:chouetteGtfsExportForAllProviders")
                .process(e -> {
                    if (e.getIn().getHeader(EXPORT_REFERENTIALS_NAMES) != null) {
                        String allReferentialsNames = e.getIn().getHeader(EXPORT_REFERENTIALS_NAMES, String.class);
                        List<String> referentialsNames = Arrays.stream(StringUtils.split(allReferentialsNames, ",")).map(s -> "mobiiti_" + s).collect(toList());
                        log.info("GTFS export global with mobi_iti providers => " + referentialsNames);
                        Collection<Provider> mobiitiProviders =  getProviderRepository().getMobiitiProviders().stream().filter(provider -> referentialsNames.contains(provider.name)).collect(Collectors.toList());
                        e.getIn().setBody(mobiitiProviders);
                    }
                    else {
                        log.info("GTFS export global with all mobi_iti providers");
                        e.getIn().setBody(getProviderRepository().getMobiitiProviders());
                    }
                })
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .setHeader(PROVIDER_ID, simple("${body.id}"))
                .setBody(constant(null))
                .inOnly("activemq:queue:ChouetteExportGtfsQueue")
                .routeId("chouette-gtfs-export-all-providers");
    }

}


