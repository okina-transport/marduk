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
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.ExportToConsumersProcessor;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.*;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Value("${google.publish.public:false}")
    private boolean publicPublication;

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmssZ");

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
                    Optional<ExportTemplate> export = ExportToConsumersProcessor.currentExport(e);
                    export.ifPresent(exp -> {
                        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                        e.getIn().setHeader(FILE_HANDLE, exportFilePath(exp, provider));
                        e.getIn().setHeader(ARCHIVE_FILE_HANDLE, exportArchiveFilePath(exp, provider));
                    });
                    //e.getIn().setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
                })
                .choice()
                    .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                    .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(publicPublication))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "uploadBlobExport")
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
        return awsExportPath(export, provider) + "/" + awsExportFileName(export);
    }

    private static String exportArchiveFilePath(ExportTemplate export, Provider provider) {
        String archive = "";
        switch (export.getType()) {
            case NETEX:
                archive = " OFFRE_" + exportSiteId(provider) + "_" + nowTag() + ".zip";
                break;
            case ARRET:
                archive = "ARRET_" + exportSiteId(provider) + "_" + exportSiteName(provider)+ "_T_" + nowTag() + ".zip";
                break;
            default:
                archive = nowTag() + "_" + awsExportFileName(export);
                break;
        }

        return awsExportPath(export, provider) + "/archive/" +  archive;
    }


    private static String awsExportFileName(ExportTemplate export) {
        String filename = "";
        switch (export.getType()) {
            case NETEX:
                filename = "netex_offre.zip";
                break;
            case GTFS:
                filename = "gtfs.zip";
                break;
            case ARRET:
                filename = "netex_arrets.zip";
                break;
            case CONCERTO:
                filename = "concerto.csv";
                break;
        }
        return filename;
    }




    private static String awsExportPath(ExportTemplate export, Provider provider) {
        return exportSiteId(provider) + "/exports/" + export.getId();
    }

    private static String exportSiteName(Provider provider) {
        return provider.chouetteInfo.referential;
    }

    private static String exportSiteId(Provider provider) {
        return "" + (provider.mosaicId != null ? provider.mosaicId : "x" + provider.getId());
    }

    private static String nowTag() {
        return sdf.format(new Date());
    }
}
