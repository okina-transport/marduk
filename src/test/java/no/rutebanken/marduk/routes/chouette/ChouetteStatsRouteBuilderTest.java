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

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class ChouetteStatsRouteBuilderTest {

    @Test
    public void testProviderMatchingLevelFilterAndProviderId() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 3l), "level1", Arrays.asList("1", "2")));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), "level2", Arrays.asList("1", "2")));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "all", Arrays.asList("1", "2")));
    }


    @Test
    public void testProviderMatchingLevelFilter() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 3l), "level1", new ArrayList<>()));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "level2", null));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), "all", null));
    }

    @Test
    public void testProviderMatchingProviderId() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), null, Arrays.asList("1", "2")));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "all", Arrays.asList("1", "2")));
    }

    @Test
    public void testProviderMatchingWhenNoFiltering() {
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1l, null), null, null));
        Assert.assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "all", new ArrayList<>()));
    }

    @Test
    public void testProviderNotMatchingWhenWrongLevelFiltering() {
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 3l), "level2", null));
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(2l, null), "level1", Arrays.asList("1", "2")));
    }

    @Test
    public void testProviderNotMatchingNotInProviderIdList() {
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(1l, 4l), "all", Arrays.asList("2")));
        Assert.assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(3l, null), "level2", Arrays.asList("1", "2")));
    }


    Provider provider(Long id, Long migrateDataToProviderId) {
        Provider provider = new Provider();
        provider.id = id;
        provider.chouetteInfo = new ChouetteInfo();
        provider.chouetteInfo.migrateDataToProvider = migrateDataToProviderId;
        return provider;
    }
}
