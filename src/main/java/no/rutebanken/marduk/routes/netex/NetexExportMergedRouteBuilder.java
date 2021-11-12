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

package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.ExportToConsumersProcessor;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.Status.ABORTED;
import static no.rutebanken.marduk.routes.chouette.json.Status.CANCELED;

/**
 * Route combining netex exports per provider with stop place export for a common netex export for Norway.
 */
@Component
public class NetexExportMergedRouteBuilder extends BaseRouteBuilder {

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @Value("${netex.export.stops.file.prefix:_stops}")
    private String netexExportStopsFilePrefix;

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    @Value("#{'${netex.global.excluded.providers:}'.split(',')}")
    private List<String> excludedProviders;

    @Value("${netex.merge.delay.before.checks:60000}")
    private Integer delayBeforeChecks;

    @Value("${netex.merge.max.retries:300}")
    private Integer maxRetries;

    @Autowired
    ExportToConsumersProcessor exportToConsumersProcessor;

    private List<String> completedExports = new ArrayList<>();
    private List<String> failedExports = new ArrayList<>();

    @Override
    public void configure() throws Exception {
        super.configure();


        from("direct:resetExportLists")
                .process(e-> {
                    completedExports.clear();
                    failedExports.clear();
                })
                .routeId("reset-export-lists");

        from("direct:updateMergedNetexStatus")
                .choice()
                .when(PredicateBuilder.or(simple("${header.current_status} == '" + ABORTED + "'"),simple("${header.current_status} == '" + CANCELED + "'")))
                    .process(e->{
                        String referential = (String) e.getIn().getHeader(CHOUETTE_REFERENTIAL);
                        failedExports.add(referential + "-" + CURRENT_AGGREGATED_NETEX_FILENAME);
                    })
                .otherwise()
                    .process(e->{
                        String referential = (String) e.getIn().getHeader(CHOUETTE_REFERENTIAL);
                        completedExports.add(referential + "-" + CURRENT_AGGREGATED_NETEX_FILENAME);
                    })
                .end()
                .routeId("update-merged-netex-status");

        from("direct:exportMergedNetex")
                .log(LoggingLevel.INFO, getClass().getName(), "Start export of merged Netex file for France")
                .setProperty(FOLDER_NAME, simple(localWorkingDirectory))
                .process(e -> {
                    String correlationId = UUID.randomUUID().toString();
                    e.getIn().setHeader(Constants.CORRELATION_ID, correlationId);
                    JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_NETEX_MERGED").fileName(netexExportStopsFilePrefix).correlationId(correlationId).state(JobEvent.State.STARTED).build();
                        })
                .setHeader(Exchange.FILE_PARENT, simple("${exchangeProperty."+FOLDER_NAME+"}" + "/netex/allFiles"))
                .inOnly("direct:checkMergedNetex")
                .routeId("netex-export-merged-route");

        from("direct:reportExportMergedNetexOK")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .inOnly("direct:updateStatus")
                .routeId("netex-export-merged-report-ok");


        from("direct:checkMergedNetex")
                .process(e -> {
                    e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1);
                    setUnfinishedExports(e);
                })
                .choice()
                .when(e -> e.getIn().getHeader(MISSING_EXPORTS, String.class) != null)
                    .log(LoggingLevel.INFO, getClass().getName(), "Waiting for next export to launch merge")
                    .delay(delayBeforeChecks)
                    .to("direct:newRetryMergeNetex")
                .endChoice()
                .otherwise()
                    .setProperty(FOLDER_NAME, simple(localWorkingDirectory))
                    .setHeader(Exchange.FILE_PARENT, simple("${exchangeProperty."+FOLDER_NAME+"}" + "/netex/allFiles"))
                    .log(LoggingLevel.INFO, getClass().getName(), "All exports have been generated. Launching merge of all export files")
                    .to("direct:fetchLatestProviderNetexExports")

                    .to("direct:mergeNetex")
                    .process(exportToConsumersProcessor)

                    .to("direct:cleanUpLocalDirectory")

                    // Use wire tap to avoid replacing body
                    .wireTap("direct:reportExportMergedNetexOK")

                    .log(LoggingLevel.INFO, getClass().getName(), "Completed export of merged Netex file for France")
                .endChoice()

                .routeId("check_merged_netex");


        from("direct:newRetryMergeNetex")
                .choice()
                .when(simple("${header.loopCounter} > " + maxRetries))
                     .log(LoggingLevel.ERROR, getClass().getName(), "Too many retries for merged netex. merged Netex stopped. Files still missing:${header.MISSING_EXPORTS}")
                    .stop()
                .otherwise()
                    // Update status
                    .to("direct:checkMergedNetex")
                .end()
                .routeId("new_retry_check_merged_netex");




        from("direct:fetchLatestProviderNetexExports")
                .log(LoggingLevel.INFO, getClass().getName(), "Fetching netex files for all providers.")
                .process(e -> e.getIn().setBody(getAggregatedNetexFiles()))
                .split(body())
                .to("direct:fetchProviderNetexExport")
                .routeId("netex-export-fetch-latest-per-provider");


        from("direct:fetchProviderNetexExport")
                .log(LoggingLevel.INFO, getClass().getName(), "Fetching mobiiti_technique/merged/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(MERGED_NETEX_ROOT_DIR + "/${property.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getProperty(FOLDER_NAME, String.class) + "/netex/allFiles"))
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("netex-export-fetch-latest-for-provider");


        from("direct:fetchStopsNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching " + stopPlaceExportBlobPath)
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(stopPlaceExportBlobPath))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class),  e.getProperty(FOLDER_NAME, String.class) + "/stops"))
                .process(e -> copyStopFiles( e.getProperty(FOLDER_NAME, String.class) + "/stops", e.getProperty(FOLDER_NAME, String.class)))
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), "No stop place export found, unable to create merged Netex for Norway")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .stop()
                .routeId("netex-export-fetch-latest-for-stops");

        from("direct:mergeNetex").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging Netex files for all providers and stop place registry.")
                .process(e -> e.getIn().setBody(new FileInputStream(ZipFileUtils.zipFilesInFolder( e.getProperty(FOLDER_NAME, String.class) + "/netex/allFiles",  e.getProperty(FOLDER_NAME, String.class) + "/netex/export_global_netex.zip"))))
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .log(LoggingLevel.INFO, getClass().getName(), "Uploaded new combined Netex for France")
                .routeId("netex-export-merge-file");

    }

    String getAggregatedNetexFiles() {
        return getProviderRepository().getProviders().stream()
                       .filter(p -> p.chouetteInfo.migrateDataToProvider == null && !p.chouetteInfo.referential.equals("mobiiti_technique") && !excludedProviders.contains(p.chouetteInfo.referential) )
                       .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_NETEX_FILENAME)
                       .collect(Collectors.joining(","));
    }


    /**
     * Read the list of expected files and compares it to the completed files to
     * identify which files are not finished and sets it in the header
     */
    private void setUnfinishedExports(Exchange e){



        List<String> expectedFiles = Arrays.asList(getAggregatedNetexFiles().split(","));

        List<String> missingFiles = expectedFiles.stream()
                                    .filter(file-> !completedExports.contains(file) && !failedExports.contains(file))
                                    .collect(Collectors.toList());

        String missingExports = missingFiles.isEmpty() ? null : missingFiles.stream().collect(Collectors.joining(","));
        e.getIn().setHeader(MISSING_EXPORTS, missingExports);
        log.info(missingExports == null ? "All netex have been generated" : "MISSING EXPORTS:" + missingExports);
        if (!failedExports.isEmpty()){
            log.info("Failed exports:" + failedExports.stream().collect(Collectors.joining(",")));
        }
    }


    /**
     * Copy stop files from stop place registry to ensure they are given a name in compliance with profile.
     */
    private void copyStopFiles(String sourceDir, String targetDir) {
        try {
            int i = 0;
            for (File stopFile : FileUtils.listFiles(new File(sourceDir), null, false)) {
                String targetFileName = netexExportStopsFilePrefix + (i > 0 ? i : "") + ".xml";
                FileUtils.copyFile(stopFile, new File(targetDir + "/" + targetFileName));
            }
        } catch (IOException ioe) {
            throw new MardukException("Failed to copy/rename stop files from NSR: " + ioe.getMessage(), ioe);
        }
    }
}
