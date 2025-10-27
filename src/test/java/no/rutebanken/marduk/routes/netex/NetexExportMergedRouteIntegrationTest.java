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

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;

@Disabled
class NetexExportMergedRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private BlobStoreRepository blobStoreRepository;

    @Produce("direct:exportMergedNetex")
    protected ProducerTemplate startRoute;

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @EndpointInject("mock:ChouetteExportGtfsQueue")
    protected MockEndpoint mockEndpoint;

    @Test
    void testExportMergedNetex() throws Exception {

        AdviceWith.adviceWith(context, "netex-export-merged-route", a -> {
            a.interceptSendToEndpoint("jms:queue:ChouetteExportGtfsQueue").skipSendToOriginalEndpoint()
                    .to("mockEndpoint");
        });

        // Create stop file in in memory blob store
        blobStoreRepository.uploadBlob(stopPlaceExportBlobPath, new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip")), false);

        // Create provider netex export in in memory blob store
        blobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-aggregated-netex.zip", new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip")), false);

        startRoute.requestBody(null);

        mockEndpoint.expectedMessageCount(1);

        Assertions.assertNotNull(blobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath), "Expected merged netex file to have been uploaded");
    }

}
