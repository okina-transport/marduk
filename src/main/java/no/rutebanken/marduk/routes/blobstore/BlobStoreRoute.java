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

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;

import static no.rutebanken.marduk.Constants.*;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "uploadBlob")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

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


    }
}
