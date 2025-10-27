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

package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScrapeResource {


    private final PrometheusMetricsService prometheusRegistry;

    public ScrapeResource(PrometheusMetricsService prometheusRegistry) {
        this.prometheusRegistry = prometheusRegistry;
    }


    @GetMapping(value ="/scrape", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> scrape() {
        return ResponseEntity.ok()
                .body(prometheusRegistry.scrape());
    }

}
