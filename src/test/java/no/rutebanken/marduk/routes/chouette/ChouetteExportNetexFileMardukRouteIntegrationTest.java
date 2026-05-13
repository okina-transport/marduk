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

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;
import static org.mockito.Mockito.when;

class ChouetteExportNetexFileMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@EndpointInject("mock:chouetteCreateExport")
	protected MockEndpoint chouetteCreateExport;

	@EndpointInject("mock:pollJobStatus")
	protected MockEndpoint pollJobStatus;

	@EndpointInject("mock:updateStatus")
	protected MockEndpoint updateStatus;

	@EndpointInject("mock:chouetteGetData")
	protected MockEndpoint chouetteGetData;

	@EndpointInject("mock:exportMergedNetex")
	protected MockEndpoint exportMergedNetex;

	@Produce(ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE)
	protected ProducerTemplate importTemplate;

	@Produce("direct:processNetexExportResult")
	protected ProducerTemplate processExportResultTemplate;

	@Value("${chouette.url}")
	private String chouetteUrl;

    @BeforeEach()
    void beforeEach() {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
    }

	@Test
	 void testExportDataspace() throws Exception {
        // Mock initial call to Chouette to import job
        AdviceWith.adviceWith(context, "chouette-start-export-netex", adviceRouteBuilder -> {
            adviceRouteBuilder.weaveByToUri(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/netexprofile")
                    .replace().to("mock:chouetteCreateExport");
            adviceRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });



		// Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well
        AdviceWith.adviceWith(context, "chouette-validate-job-status-parameters", adviceRouteBuilder -> {
            adviceRouteBuilder.interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
                    .to("mock:pollJobStatus");
        });

		// Mock update status calls
        AdviceWith.adviceWith(context, "chouette-process-export-netex-status", adviceRouteBuilder -> {
            adviceRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            adviceRouteBuilder.interceptSendToEndpoint("direct:exportMergedNetex").skipSendToOriginalEndpoint()
                    .to("mock:exportMergedNetex");
        });


        AdviceWith.adviceWith(context, "chouette-get-job-status", adviceRouteBuilder -> {
            adviceRouteBuilder.interceptSendToEndpoint(chouetteUrl+ "/chouette_iev/referentials/rut/jobs/1/data")
                    .skipSendToOriginalEndpoint().to("mock:chouetteGetData");
        });

		chouetteGetData.expectedMessageCount(0);
		chouetteGetData.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			 public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					// Should be GTFS contnet
					return (T) Files.readString(Paths.get("/no/rutebanken/marduk/chouette/getActionReportResponseOK.json"), StandardCharsets.UTF_8);
				} catch (IOException e) {
					return null;
				}
			}
		});

		// we must manually start when we are done with all the advice with
		context.start();

		// 1 initial import call
		chouetteCreateExport.expectedMessageCount(1);
		chouetteCreateExport.returnReplyHeader("Location",
                new SimpleExpression(chouetteUrl.replace("http4://", "http://")
                        + "/chouette_iev/referentials/rut/scheduled_jobs/1"));


		pollJobStatus.expectedMessageCount(1);
		updateStatus.expectedMessageCount(1);


		exportMergedNetex.expectedMessageCount(1);

		Map<String, Object> headers = new HashMap<>();
		headers.put(Constants.PROVIDER_ID, "2");
		headers.put(NETEX_EXPORT_GLOBAL, false);
		headers.put(NO_GTFS_EXPORT, true);

		importTemplate.sendBodyAndHeaders(null, headers);

		chouetteCreateExport.assertIsSatisfied();
		pollJobStatus.assertIsSatisfied();

		Exchange exchange = pollJobStatus.getReceivedExchanges().getFirst();
		exchange.getIn().setHeader("action_report_result", "OK");
		exchange.getIn().setHeader("data_url", chouetteUrl+ "/chouette_iev/referentials/rut/jobs/1/data");
		processExportResultTemplate.send(exchange );

		chouetteGetData.assertIsSatisfied();
		updateStatus.assertIsSatisfied();

	}
}
