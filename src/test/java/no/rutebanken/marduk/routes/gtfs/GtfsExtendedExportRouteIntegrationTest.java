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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

import static org.mockito.Mockito.when;

@Disabled
class GtfsExtendedExportRouteIntegrationTest  extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private BlobStoreRepository blobStoreRepository;

    @Produce("direct:exportGtfsExtendedMerged")
    protected ProducerTemplate startRoute;


    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String exportFileName;


    @BeforeEach
    void prepare()  {
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null), provider("rb_rut", 2, null), provider("opp", 3, 4l)));
    }

    @Test
    @Disabled()
    void testUploadExtendedGtfsMergedFile() {
        //TODO to rewrite after huge modifications on gtfs routes
    }

}
