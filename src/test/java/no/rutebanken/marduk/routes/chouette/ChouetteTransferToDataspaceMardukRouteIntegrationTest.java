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
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;
import static org.mockito.Mockito.when;

class ChouetteTransferToDataspaceMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@EndpointInject("mock:chouetteCreateExport")
	protected MockEndpoint chouetteCreateExport;

	@EndpointInject("mock:pollJobStatus")
	protected MockEndpoint pollJobStatus;

	@EndpointInject("mock:checkScheduledJobsBeforeTriggeringNextAction")
	protected MockEndpoint checkScheduledJobsBeforeTriggeringNextAction;

	@EndpointInject("mock:updateStatus")
	protected MockEndpoint updateStatus;

	@Produce("jms:queue:ChouetteTransferExportQueue")
	protected ProducerTemplate transferTemplate;

	@Produce("direct:processTransferExportResult")
	protected ProducerTemplate processTransferExportResultTemplate;
	
	@Value("${chouette.url}")
	private String chouetteUrl;

    @BeforeEach
    void beforeEach() {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
    }

	@Test
	void testTransferDataToDataspaceDataspace() throws Exception {

		// Mock initial call to Chouette to export job
        AdviceWith.adviceWith(context, "chouette-send-transfer-job", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.weaveByToUri(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/transfer")
                    .replace().to("mock:chouetteCreateExport");

            adviceWithRouteBuilder.interceptSendToEndpoint("jms:queue:ChouettePollStatusQueue")
                    .skipSendToOriginalEndpoint().to("mock:pollJobStatus");

            adviceWithRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });

	

		// Mock update status calls
        AdviceWith.adviceWith(context, "chouette-process-transfer-status", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");

            adviceWithRouteBuilder.interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringRBSpaceValidation").skipSendToOriginalEndpoint()
                    .to("mock:checkScheduledJobsBeforeTriggeringNextAction");
        });

	

		// we must manually start when we are done with all the advice with
		context.start();

		
		// 1 initial import call
		chouetteCreateExport.expectedMessageCount(1);
		chouetteCreateExport.returnReplyHeader("Location", new SimpleExpression(
				chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));

	
		pollJobStatus.expectedMessageCount(1);
		updateStatus.expectedMessageCount(2);
		checkScheduledJobsBeforeTriggeringNextAction.expectedMessageCount(1);
		
		
		Map<String, Object> headers = new HashMap<>();
		headers.put(Constants.PROVIDER_ID, "2");
		transferTemplate.sendBodyAndHeaders(null, headers);
		

		Map<String, Object> importJobCompletedHeaders = new HashMap<>();
		importJobCompletedHeaders.put(Constants.PROVIDER_ID, "2");
		importJobCompletedHeaders.put("action_report_result", "OK");
		importJobCompletedHeaders.put("validation_report_result", "OK");
		importJobCompletedHeaders.put(Constants.FILE_HANDLE, "None");
		importJobCompletedHeaders.put(Constants.CORRELATION_ID, "None");
		processTransferExportResultTemplate.sendBodyAndHeaders(null, importJobCompletedHeaders);

		chouetteCreateExport.assertIsSatisfied();
		pollJobStatus.assertIsSatisfied();
		
		checkScheduledJobsBeforeTriggeringNextAction.assertIsSatisfied();
		updateStatus.assertIsSatisfied();

	}

  



}
