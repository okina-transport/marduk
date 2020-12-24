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

package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.ExportToConsumersProcessor;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.*;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");

    @Override
    public void configure() throws Exception {

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                    .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                    .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "uploadBlob")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:uploadBlobExport")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    Provider provider = getProviderRepository().getNonMosaicProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))
                            .orElseThrow(() -> new RuntimeException("No valid base provider found for export uploading. Provider id : " + e.getIn().getHeader(PROVIDER_ID)));
                    Optional<ExportTemplate> export = ExportToConsumersProcessor.currentExport(e);
                    if (export.isPresent()) {
                        e.getIn().setHeader(FILE_HANDLE, exportFilePath(export.get(), provider));
                        e.getIn().setHeader(ARCHIVE_FILE_HANDLE, exportArchiveFilePath(export.get(), provider, e.getIn().getHeader(EXPORT_FILE_NAME).toString()));
                        e.getIn().setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, export.get().hasExportFilePublicAccess());

                        if(export.get().getConsumers() != null && !export.get().getConsumers().isEmpty()) {
                            e.getIn().setHeader(NOTIFICATION, export.get().getConsumers().get(0).isNotification());
                            if (e.getIn().getHeader(NOTIFICATION).equals(true)) {
                                e.getIn().setHeader(NOTIFICATION_URL, export.get().getConsumers().get(0).getNotificationUrl());
                            }
                        }
                    } else { // cas des exports manuels
                        e.getIn().setHeader(FILE_HANDLE, exportFilePath(provider, e.getIn().getHeader(EXPORT_FILE_NAME).toString()));
                    }
                })
                .choice()
                    .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                    .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "uploadBlobExport")
                .choice()
                    .when(header(NOTIFICATION).isEqualTo(true))
                    .bean("notificationService", "sendNotification")
                .end()
                .setBody(simple(""))
                .routeId("blobstore-upload-export");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("blobStoreService", "getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");

        from("direct:listBlobs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean("blobStoreService", "listBlobs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list");

        from("direct:listBlobsFlat")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean("blobStoreService", "listBlobsFlat")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list-flat");

        from("direct:listBlobsInFolders")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("blobStoreService", "listBlobsInFolders")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store for multiple folders.")
                .routeId("blobstore-list-in-folders");

        from("direct:listBlobsInFoldersByProvider")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean("blobStoreService", "listBlobsInFoldersByProvider")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list-in-folders-by-provider");


    }



    public static String exportFilePath(ExportTemplate export, Provider provider) {
        if(export.getType().equals(ExportType.CONCERTO)){
          return "concerto_idfm/exports/" + export.getId() + "/" + awsExportFileFormat(export);
        }
        return awsExportPath(export, provider) + "/" + awsExportFileName(export);
    }

    public static String exportFilePath(Provider provider, String filename) {
        if(filename.endsWith("csv")){
            return "concerto_idfm/exports/0-manuals/" + filename;
        }
        return exportSiteId(provider) + "/exports/0-manuals/" + filename;
    }

    public static String exportValidationFilePath(Provider provider, String filename, String jobId){
        return exportSiteId(provider) + "/logs/" + jobId + "/" + filename;
    }

    private static String exportArchiveFilePath(ExportTemplate export, Provider provider, String fileName) {
        return awsExportPath(export, provider) + "/archive/" +  fileName;
    }


    private static String awsExportFileName(ExportTemplate export) {
        return awsExportFileFormat(export) + "." + awsExportFileExtension(export);
    }

    private static String awsExportFileFormat(ExportTemplate export) {
        String format = "";
        switch (export.getType()) {
            case NETEX:
                format = "netex_offre";
                break;
            case GTFS:
                format = "gtfs";
                break;
            case ARRET:
                format = "netex_arrets";
                break;
            case CONCERTO:
                format = "concerto";
                break;
            default:
                format = "offre";
                break;
        }
        return format;
    }

    private static String awsExportFileExtension(ExportTemplate export) {
        String format = "";
        switch (export.getType()) {
            case CONCERTO:
                format = "csv";
                break;
            case NETEX:
                format = "zip";
                break;
            case GTFS:
                format = "zip";
                break;
            case ARRET:
            default:
                format = "zip";
                break;
        }
        return format;
    }


    /**
     * Returns the AWS path for storing imports
     * @param provider
     * @return
     */
    public static String awsImportPath(Provider provider) {
        return exportSiteId(provider) + "/imports";
    }


    private static String awsExportPath(ExportTemplate export, Provider provider) {
        return exportSiteId(provider) + "/exports/" + export.getId();
    }

    public static String exportSiteId(Provider provider) {
        return "" + (provider.mosaicId != null ? provider.mosaicId : "x" + provider.getId());
    }
}
