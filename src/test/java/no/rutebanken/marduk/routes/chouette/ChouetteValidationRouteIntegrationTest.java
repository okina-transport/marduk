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
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

class ChouetteValidationRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:chouetteCreateValidation")
    protected MockEndpoint chouetteCreateValidation;

    @EndpointInject("mock:pollJobStatus")
    protected MockEndpoint pollJobStatus;

    @EndpointInject("mock:chouetteGetJobsForProvider")
    protected MockEndpoint chouetteGetJobs;

    @EndpointInject("mock:processValidationResult")
    protected MockEndpoint processValidationResult;

    @EndpointInject("mock:chouetteTransferExportQueue")
    protected MockEndpoint chouetteTransferExportQueue;

    @EndpointInject("mock:checkScheduledJobsBeforeTriggeringExport")
    protected MockEndpoint chouetteCheckScheduledJobs;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("jms:queue:ChouetteValidationQueue")
    protected ProducerTemplate validationTemplate;

    @Produce("direct:processValidationResult")
    protected ProducerTemplate processValidationResultTemplate;

    @Produce("direct:checkScheduledJobsBeforeTriggeringExport")
    protected ProducerTemplate triggerJobListTemplate;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @BeforeEach
    void beforeEach() {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
        chouetteCreateValidation.reset();
        pollJobStatus.reset();
        chouetteGetJobs.reset();
        processValidationResult.reset();
        chouetteTransferExportQueue.reset();
        chouetteCheckScheduledJobs.reset();
        updateStatus.reset();
    }


    @Test
    void testJobListResponseScheduled() throws Exception {
        testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json", false);
    }

    void testJobListResponse(String jobListResponseClasspathReference, boolean expectExport) throws Exception {

        AdviceWith.adviceWith(context, "chouette-process-job-list-after-validation", adviceRouteBuilder -> {
            adviceRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/*")
                    .skipSendToOriginalEndpoint()
                    .to("mock:chouetteGetJobsForProvider");
            adviceRouteBuilder.interceptSendToEndpoint("jms:queue:ChouetteTransferExportQueue")
                    .skipSendToOriginalEndpoint()
                    .to("mock:chouetteTransferExportQueue");
        });

        context.start();

        // 1 call to list other import jobs in referential
        chouetteGetJobs.expectedMessageCount(1);
        chouetteGetJobs.returnReplyBody(new Expression() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T evaluate(Exchange ex, Class<T> arg1) {
                try {
                    return (T) IOUtils.toString(getClass().getResourceAsStream(jobListResponseClasspathReference));
                } catch (IOException e) {
                    return null;
                }
            }
        });

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.CHOUETTE_REFERENTIAL, "rut");
        headers.put(Constants.PROVIDER_ID, 2);

        triggerJobListTemplate.sendBodyAndHeaders(null, headers);

        chouetteGetJobs.assertIsSatisfied();

        if (expectExport) {
            chouetteTransferExportQueue.expectedMessageCount(1);
        }
        chouetteTransferExportQueue.assertIsSatisfied();

    }

}
