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
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.status.JobEvent;
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
import java.util.concurrent.atomic.AtomicInteger;

import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ChouettePollJobStatusMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:destination")
    protected MockEndpoint destination;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;
    @EndpointInject("mock:chouetteGetJobStatus")
    protected MockEndpoint chouetteGetJobStatus;

    @EndpointInject("mock:chouetteGetActionReport")
    protected MockEndpoint chouetteGetActionReport;

    @EndpointInject("mock:chouetteGetValidationReport")
    protected MockEndpoint chouetteGetValidationReport;

    @Produce("jms:queue:ChouettePollStatusQueue")
    protected ProducerTemplate pollStartTemplate;

    @Produce("direct:checkValidationReport")
    protected ProducerTemplate validationReportTemplate;

    @EndpointInject("mock:chouetteGetJobsForProvider")
    protected MockEndpoint getJobs;

    @Produce("direct:chouetteGetJobsForProvider")
    protected ProducerTemplate getJobsTemplate;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @BeforeEach
    void beforeEach() {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
    }

    //does not work anymore with new poll status request
    public void testPollJobStatus() throws Exception {

        // Mock get status call to chouette
        AdviceWith.adviceWith(context, "chouette-get-job-status", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/scheduled_jobs/1")
                    .skipSendToOriginalEndpoint().to("mock:chouetteGetJobStatus");

            adviceWithRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint().to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "chouette-process-job-reports", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data/1/action_report.json")
                    .skipSendToOriginalEndpoint().to("mock:chouetteGetActionReport");

            adviceWithRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data/1/validation_report.json")
                    .skipSendToOriginalEndpoint().to("mock:chouetteGetValidationReport");
        });

        AdviceWith.adviceWith(context, "chouette-reschedule-job", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint().to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "chouette-reschedule-job", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(ROUTE_UPDATE_STATUS).skipSendToOriginalEndpoint().to("mock:updateStatus");
        });


        // we must manually start when we are done with all the advice with
        context.start();

        // 2 status calls, first return SCHEDULED, then TERMINATED
        final AtomicInteger reportCounter = new AtomicInteger(0);
        chouetteGetJobStatus.expectedMessageCount(2);
        chouetteGetJobStatus.returnReplyBody(new Expression() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T evaluate(Exchange ex, Class<T> arg1) {
                try {
                    int currval = reportCounter.getAndIncrement();
                    if (currval == 0) {
                        return (T) IOUtils.toString(getClass().getResourceAsStream(
                                "/no/rutebanken/marduk/chouette/getJobStatusResponseStarted.json"));

                    } else {
                        return (T) IOUtils.toString(getClass().getResourceAsStream(
                                "/no/rutebanken/marduk/chouette/getJobStatusResponseTerminated.json"));

                    }
                } catch (IOException e) {
                    return null;
                }
            }
        });

        // 1 aciton report call
        chouetteGetActionReport.expectedMessageCount(1);
        chouetteGetActionReport.returnReplyBody(new Expression() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T evaluate(Exchange ex, Class<T> arg1) {
                try {
                    return (T) IOUtils.toString(getClass()
                            .getResourceAsStream("/no/rutebanken/marduk/chouette/getActionReportResponseOK.json"));
                } catch (IOException e) {
                    return null;
                }
            }
        });

        // 1 aciton report call
        chouetteGetValidationReport.expectedMessageCount(1);
        chouetteGetValidationReport.returnReplyBody(new Expression() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T evaluate(Exchange ex, Class<T> arg1) {
                try {
                    return (T) IOUtils.toString(getClass()
                            .getResourceAsStream("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json"));
                } catch (IOException e) {
                    return null;
                }
            }
        });

        // Should end up here with 2 headers
        destination.expectedHeaderReceived("validation_report_result", "OK");
        destination.expectedHeaderReceived("action_report_result", "OK");
        destination.expectedMessageCount(1);

        updateStatus.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.PROVIDER_ID, "0");
        headers.put(Constants.FILE_NAME, "file_name");
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, "rut/file_name");
        headers.put(Constants.JOB_STATUS_ROUTING_DESTINATION, "mock:destination");
        headers.put(Constants.JOB_STATUS_URL, chouetteUrl + "/chouette_iev/referentials/rut/scheduled_jobs/1");
        headers.put(Constants.JOB_STATUS_JOB_TYPE, JobEvent.TimetableAction.IMPORT.name());
        pollStartTemplate.sendBodyAndHeaders(null, headers);

        chouetteGetJobStatus.assertIsSatisfied();
        chouetteGetActionReport.assertIsSatisfied();
        chouetteGetValidationReport.assertIsSatisfied();
        destination.assertIsSatisfied();
        updateStatus.assertIsSatisfied();
    }

    //@Test
    public void testValidationReportResultOK() throws Exception {
        testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json", "OK");
    }

    //@Test
    public void testValidationReportResultNOK() throws Exception {
        testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseNOK.json", "NOK");
    }

    public void testValidationReportResult(String validationReportClasspathReference, String expectedResult)
            throws Exception {

        context.start();

        validationReportTemplate.sendBodyAndHeader(getClass().getResourceAsStream(validationReportClasspathReference),
                Constants.JOB_STATUS_ROUTING_DESTINATION, "mock:destination");

        destination.expectedMessageCount(1);
        destination.expectedHeaderReceived("validation_report_result", expectedResult);
        destination.assertIsSatisfied();

    }

    @Test
    void getJobs() throws Exception {
        AdviceWith.adviceWith(context, "chouette-list-jobs", adviceRouteBuilder -> {
            adviceRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/jobs?addActionParameters=false")
                    .skipSendToOriginalEndpoint()
                    .to("mock:chouetteGetJobsForProvider");
        });


        context.start();

        getJobs.returnReplyBody(new Expression() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T evaluate(Exchange ex, Class<T> arg1) {
                try {
                    return (T) IOUtils.toString(getClass()
                            .getResourceAsStream("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json"));
                } catch (IOException e) {
                    return null;
                }
            }
        });


        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(Constants.PROVIDER_ID, "2");
        JobResponse[] rsp = (JobResponse[]) getJobsTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        assertThat(rsp).isNotEmpty();
    }


}
