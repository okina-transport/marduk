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

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Disabled
class GtfsBasicExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private BlobStoreRepository blobStoreRepository;

    @Produce("direct:exportGtfsBasicMerged")
    protected ProducerTemplate startRoute;


    @Value("${gtfs.basic.norway.merged.file.name:rb_norway-aggregated-gtfs-basic.zip}")
    private String exportFileName;


    @Test
    void testUploadBasicGtfsMergedFile() {
        //TODO To rewrite after huge modifications on merged GTFS export
//        context.start();
//
//        String pathname = "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip";
//
//        //populate fake blob repo
//        blobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(new File(pathname)), false);
//        blobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_avi-aggregated-gtfs.zip", new FileInputStream(new File(pathname)), false);
//
//
//
//        Map<String, Object> headers = new HashMap<String, Object>();
//
//        headers.put(EXPORT_REFERENTIALS_NAMES, "testExport");
//        startRoute.requestBodyAndHeaders(null,headers);
//
//        InputStream mergedIS = blobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + exportFileName);
//        Assert.assertNotNull("Expected transformed gtfs file to have been uploaded", mergedIS);
//
//        File mergedFile = File.createTempFile("mergedID", "tmp");
//        FileUtils.copyInputStreamToFile(mergedIS, mergedFile);
//        GtfsTransformationServiceTest.assertRouteRouteTypesAreConvertedToBasicGtfsValues(mergedFile);
    }

}
