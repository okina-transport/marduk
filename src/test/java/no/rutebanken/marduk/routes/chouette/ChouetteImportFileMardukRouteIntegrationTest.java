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
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_IMPORT_LAUNCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ChouetteImportFileMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private BlobStoreRepository blobStoreRepository;

    @EndpointInject("mock:chouetteCreateImport")
    protected MockEndpoint chouetteCreateImport;

    @EndpointInject("mock:pollJobStatus")
    protected MockEndpoint pollJobStatus;

    @EndpointInject("mock:chouetteGetJobsForProvider")
    protected MockEndpoint chouetteGetJobs;

    @EndpointInject("mock:processImportResult")
    protected MockEndpoint processActionReportResult;

    @EndpointInject("mock:chouetteValidationQueue")
    protected MockEndpoint chouetteValidationQueue;

    @EndpointInject("mock:checkScheduledJobsBeforeTriggeringNextAction")
    protected MockEndpoint checkScheduledJobsBeforeTriggeringNextAction;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce(ROUTE_IMPORT_LAUNCH)
    protected ProducerTemplate importTemplate;

    @Produce("direct:processImportResult")
    protected ProducerTemplate processImportResultTemplate;

    @Produce("direct:checkScheduledJobsBeforeTriggeringNextAction")
    protected ProducerTemplate triggerJobListTemplate;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @BeforeEach
    void beforeEach() {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getNonMobiitiProvider(2L)).thenReturn(Optional.of(providers.get(0)));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
        chouetteCreateImport.reset();
        pollJobStatus.reset();
        chouetteGetJobs.reset();
        processActionReportResult.reset();
        chouetteValidationQueue.reset();
        checkScheduledJobsBeforeTriggeringNextAction.reset();
        updateStatus.reset();
    }

    @Test
    void testImportFileToDataspace() throws Exception {

        String filename = "ruter_fake_data.zip";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";

        //populate fake blob repo
        FileInputStream fileInputStream = new FileInputStream(pathname);
        blobStoreRepository.uploadBlob("rut/" + filename, fileInputStream, false);
        // Mock initial call to Chouette to import job
        AdviceWith.adviceWith(context, "chouette-send-import-job", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/importer/regtopp")
                    .skipSendToOriginalEndpoint().to("mock:chouetteCreateImport");
        });

        // Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well
        AdviceWith.adviceWith(context, "chouette-validate-job-status-parameters", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
                    .to("mock:pollJobStatus");
        });

        // Mock update status calls
        AdviceWith.adviceWith(context, "chouette-process-import-status", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            adviceWithRouteBuilder.interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringNextAction").skipSendToOriginalEndpoint()
                    .to("mock:checkScheduledJobsBeforeTriggeringNextAction");
        });

        // we must manually start when we are done with all the advice with
        context.start();

        // 1 initial import call
        chouetteCreateImport.expectedMessageCount(1);
        chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(
                                                                                       chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));


        pollJobStatus.expectedMessageCount(1);


        updateStatus.expectedMessageCount(6);
        checkScheduledJobsBeforeTriggeringNextAction.expectedMessageCount(1);


        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.FILE_NAME, filename);
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, "rut/" + filename);
        importTemplate.sendBodyAndHeaders(Path.of(pathname).toFile(), headers);

        chouetteCreateImport.assertIsSatisfied();
        pollJobStatus.assertIsSatisfied();

        Exchange exchange = pollJobStatus.getReceivedExchanges().getFirst();
        exchange.getIn().setHeader("action_report_result", "OK");
        exchange.getIn().setHeader("validation_report_result", "OK");
        processImportResultTemplate.send(exchange);

        checkScheduledJobsBeforeTriggeringNextAction.assertIsSatisfied();
        updateStatus.assertIsSatisfied();


    }

    @Test
    @Disabled("Invalid file should not be send to chouette")
    void testImportInvalidFileToDataspace() throws Exception {

        String filename = "ruter_gtfs_folder.zip";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs-folder.zip";

        //populate fake blob repo
        blobStoreRepository.uploadBlob(filename, new FileInputStream(new File(pathname)), false);

        // Mock initial call to Chouette to import job
        AdviceWith.adviceWith(context, "chouette-send-import-job", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/importer/gtfs")
                    .skipSendToOriginalEndpoint().to("mock:chouetteCreateImport");
        });


        // Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well
        AdviceWith.adviceWith(context, "chouette-validate-job-status-parameters", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
                    .to("mock:pollJobStatus");
        });

        // Mock update status calls
        AdviceWith.adviceWith(context, "chouette-process-import-status", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            adviceWithRouteBuilder.interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringNextAction").skipSendToOriginalEndpoint()
                    .to("mock:checkScheduledJobsBeforeTriggeringNextAction");
        });

        // we must manually start when we are done with all the advice with
        context.start();

        // 1 initial import call
        chouetteCreateImport.expectedMessageCount(1);
        chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(
                                                                                       chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));


        pollJobStatus.expectedMessageCount(1);

        updateStatus.expectedMessageCount(1);
        checkScheduledJobsBeforeTriggeringNextAction.expectedMessageCount(1);


        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.FILE_NAME, filename);
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, filename);

        assertThat(ZipFileUtils.zipFileContainsSingleFolder(IOUtils.toByteArray(blobStoreRepository.getBlob(filename)))).isTrue();
        importTemplate.sendBodyAndHeaders(null, headers);

        chouetteCreateImport.assertIsSatisfied();
        pollJobStatus.assertIsSatisfied();

        Exchange exchange = pollJobStatus.getReceivedExchanges().getFirst();
        exchange.getIn().setHeader("action_report_result", "OK");
        exchange.getIn().setHeader("validation_report_result", "OK");
        processImportResultTemplate.send(exchange);

        checkScheduledJobsBeforeTriggeringNextAction.assertIsSatisfied();
        updateStatus.assertIsSatisfied();

        assertThat(ZipFileUtils.zipFileContainsSingleFolder(IOUtils.toByteArray(blobStoreRepository.getBlob(filename)))).isFalse();
    }


    @Test
    void testJobListResponseTerminated() throws Exception {
        testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseAllTerminated.json", true);
    }

    @Test
    void testJobListResponseScheduled() throws Exception {
        testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json", false);
    }

    public void testJobListResponse(String jobListResponseClasspathReference, boolean expectExport) throws Exception {

        AdviceWith.adviceWith(context, "chouette-process-job-list-after-import", adviceWithRouteBuilder -> {
            adviceWithRouteBuilder.interceptSendToEndpoint(chouetteUrl + "/*")
                    .skipSendToOriginalEndpoint()
                    .to("mock:chouetteGetJobsForProvider");
            adviceWithRouteBuilder. interceptSendToEndpoint("jms:queue:ChouetteValidationQueue")
                    .skipSendToOriginalEndpoint()
                    .to("mock:chouetteValidationQueue");
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
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.CHOUETTE_REFERENTIAL, "rut");
        headers.put(Constants.ENABLE_VALIDATION, true);

        triggerJobListTemplate.sendBodyAndHeaders(null, headers);

        chouetteGetJobs.assertIsSatisfied();

        if (expectExport) {
            chouetteValidationQueue.expectedMessageCount(1);
        }
        chouetteValidationQueue.assertIsSatisfied();

    }

}
