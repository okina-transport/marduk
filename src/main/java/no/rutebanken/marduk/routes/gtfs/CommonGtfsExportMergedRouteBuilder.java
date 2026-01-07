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

package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.GtfsFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.LoggingLevel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.Exchange.FILE_PARENT;

/**
 * Common routes for building GTFS exports.
 */
@Component
public class CommonGtfsExportMergedRouteBuilder extends BaseRouteBuilder {


    @Value("${gtfs.export.download.directory:files/gtfs/merged}")
    private String localWorkingDirectory;

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    @Autowired
    FileSystemService fileSystemService;

    @Value("${gtfs.merged.tmp.working.directory:/tmp/mergedGtfs/allFiles}")
    private String mergedGtfsTmpDirectory;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:exportMergedGtfs")
                .log(LoggingLevel.INFO, getClass().getName(), "Start export of merged GTFS file for France")
                .setProperty(FOLDER_NAME, simple(localWorkingDirectory))
                .setHeader(GTFS_EXPORT_GLOBAL_OK, simple("true"))
                .toD("${header.data_url}")
                .toD("file:${exchangeProperty." + FOLDER_NAME + "}/gtfs/${header.ID_FORMAT}?fileName=" + EXPORT_GLOBAL_GTFS_ZIP)
                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of merged GTFS file for France")
                .end()
                .routeId("gtfs-export-merged-route");


        from("direct:reportExportMergedGtfsOK")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .routeId("gtfs-export-merged-report-ok");

        from("direct:fetchLatestGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs files for all providers.")
                .process(e -> e.getIn().setBody(getAggregatedGtfsFiles((String) e.getIn().getHeader(EXPORT_REFERENTIALS_NAMES))))
                        .split(body())
                        .to("direct:getGtfsFiles")
                        .routeId("gtfs-export-fetch-latest");

        from("direct:getGtfsFiles")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetching mobiiti_technique/gtfs/allFiles/${header.ID_FORMAT}/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple("mobiiti_technique/gtfs/allFiles/${header.ID_FORMAT}/${exchangeProperty.fileName}"))
                .choice()
                .when(e -> fileSystemService.isExists(e.getIn().getHeader(FILE_HANDLE, String.class)))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:${header." + FILE_PARENT + "}+/${header.ID_FORMAT}?fileName=${exchangeProperty.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "${exchangeProperty.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("gtfs-export-get-latest-for-provider");

        from("direct:mergeGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging gtfs files for all providers.")
                .delay(5000)
                .setBody(simple("${header." + FILE_PARENT + "}/${header.ID_FORMAT}" ))
                .bean(method(GtfsFileUtils.class, "mergeGtfsFilesInDirectory"))
                .toD("file:${exchangeProperty." + FOLDER_NAME + "}/gtfs/${header.ID_FORMAT}?fileName=" + EXPORT_GLOBAL_GTFS_ZIP)
                .delay(10000)
                .setHeader(FILE_HANDLE, simple("mobiiti_technique/gtfs/${header.ID_FORMAT}/" + EXPORT_GLOBAL_GTFS_ZIP))
                .to("direct:getBlob")
                .routeId("gtfs-export-merge");

        from("direct:transformGtfs")
                .choice().when(simple("${exchangeProperty." + Constants.TRANSFORMATION_ROUTING_DESTINATION + "} != null"))
                .toD("${exchangeProperty." + Constants.TRANSFORMATION_ROUTING_DESTINATION + "}")
                .routeId("gtfs-export-merged-transform");

        from("direct:uploadMergedGtfs")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, getClass().getName(), "Uploaded new merged GTFS file: ${header." + FILE_NAME + "}")
                .routeId("gtfs-export-upload-merged");


    }

    String getAggregatedGtfsFiles(String allReferentialsNames) {
        if(StringUtils.isNotEmpty(allReferentialsNames)){
            List<String> referentialsNames = Arrays.stream(StringUtils.split(allReferentialsNames, ",")).map(s -> "mobiiti_" + s).collect(toList());
            return getProviderRepository().getProviders().stream()
                    .filter(p -> p.chouetteInfo.migrateDataToProvider == null && !p.chouetteInfo.referential.equals("mobiiti_technique"))
                    .filter(provider -> referentialsNames.contains(provider.name))
                    .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                    .collect(Collectors.joining(","));
        }
        else{
            return getProviderRepository().getProviders().stream()
                    .filter(p -> p.chouetteInfo.migrateDataToProvider == null && !p.chouetteInfo.referential.equals("mobiiti_technique"))
                    .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                    .collect(Collectors.joining(","));
        }
    }
}

