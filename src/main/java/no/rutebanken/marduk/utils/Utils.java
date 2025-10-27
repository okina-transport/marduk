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

package no.rutebanken.marduk.utils;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;

public class Utils {

    private Utils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getHttp4(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }

        if (url.contains("https")) {
            return url.replaceFirst("https4", "https");
        }
        return url.replaceFirst("http4", "http");
    }

    public static Long getLastPathElementOfUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1, url.length()));
    }

    public static Provider parseProviderFromFileName(CacheProviderRepository providerRepository, String fileName) {
        if (fileName == null) {
            return null;
        }

        String[] fileParts = fileName.split("/");
        String potentialRef = fileParts[fileParts.length - 1].split("-")[0];


        return providerRepository.getProviders().stream().filter(provider -> potentialRef.equalsIgnoreCase((provider.chouetteInfo.referential))).findFirst().orElse(null);
    }
}
