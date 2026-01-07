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

package no.rutebanken.marduk;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@CamelSpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class MardukRouteBuilderIntegrationTestBase extends MardukSpringBootBaseTest {

    @Autowired
    protected CamelContext context;

    @MockitoBean
    protected CacheProviderRepository providerRepository;

    protected static List<Provider> providers;

    @BeforeAll
    static void setUp() throws IOException {
        providers = new CopyOnWriteArrayList<>();
        providers.add(Provider.create(IOUtils.toString(new FileReader("src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));
        providers.add(Provider.create(IOUtils.toString(new FileReader("src/test/resources/no/rutebanken/marduk/providerRepository/provider3.json"))));
        providers.add(Provider.create(IOUtils.toString(new FileReader("src/test/resources/no/rutebanken/marduk/providerRepository/provider4.json"))));
    }

    protected Provider provider(String ref, long id, Long migrateToProvider) {
        Provider provider = new Provider();
        provider.mobiitiId = id;
        provider.chouetteInfo = new ChouetteInfo();
        provider.chouetteInfo.referential = ref;
        provider.chouetteInfo.migrateDataToProvider = migrateToProvider;
        provider.id = id;

        return provider;
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

}
