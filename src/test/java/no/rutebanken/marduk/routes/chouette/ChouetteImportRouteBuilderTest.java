package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.ImportGenerateMapMatching;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.JSON_PART;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ChouetteImportRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ChouetteImportRouteBuilderTest  extends MardukRouteBuilderIntegrationTestBase {

    private static final String PARAMETERS = "parameters";
    private static final String GTFS_IMPORT = "gtfs-import";

    @Autowired
    private ModelCamelContext context;

    @Produce(uri = "direct:addImportParameters")
    protected ProducerTemplate addImportParametersTemplate;

    @EndpointInject(uri = "mock:sendImportJobRequest")
    protected MockEndpoint sendImportJobRequest;

    @Before
    @Override
    public void setUp() throws IOException {
        super.setUp();
        sendImportJobRequest.reset();
    }

    @Test
    public void testImportParametersDefaultMapMatching() throws Exception {
        context.getRouteDefinition("chouette-import-addToExchange-parameters").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("direct:sendImportJobRequest")
                        .skipSendToOriginalEndpoint().to("mock:sendImportJobRequest");
            }
        });

        context.start();

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.FILE_NAME, "file_name");
        headers.put(Constants.FILE_TYPE, FileType.GTFS.name());
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, "rut/file_name");
        headers.put(Constants.JOB_STATUS_ROUTING_DESTINATION, "mock:destination");
        headers.put(Constants.JOB_STATUS_JOB_TYPE, JobEvent.TimetableAction.IMPORT.name());

        addImportParametersTemplate.sendBodyAndHeaders(null, headers);

        sendImportJobRequest.expectedMessageCount(1);
        sendImportJobRequest.assertIsSatisfied();
        List<Exchange> receivedExchange = sendImportJobRequest.getExchanges();
        assertThat(receivedExchange).isNotEmpty().hasSize(1);
        Exchange actualExchange = receivedExchange.get(0);
        assertThat(actualExchange).isNotNull();
        Object header = actualExchange.getIn().getHeader(JSON_PART);
        assertThat(header).isNotNull().isInstanceOf(String.class);
        String headerValue = (String) header;
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> parametersMap = mapper.readValue(headerValue, Map.class);
        assertThat(parametersMap).containsKey(PARAMETERS);
        Map<String, Object> importParameters = (Map<String, Object>) parametersMap.get(PARAMETERS);
        assertThat(importParameters).isNotEmpty().containsKey(GTFS_IMPORT);
        Map<String, String> gtfsImport = (Map<String, String>) importParameters.get(GTFS_IMPORT);
        assertThat(gtfsImport).containsEntry("generate_map_matching", ImportGenerateMapMatching.NONE.name());
    }

    @Test
    public void testImportParametersAirMapMatching() throws Exception {
        context.getRouteDefinition("chouette-import-addToExchange-parameters").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("direct:sendImportJobRequest")
                        .skipSendToOriginalEndpoint().to("mock:sendImportJobRequest");
            }
        });

        context.start();

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.FILE_NAME, "file_name");
        headers.put(Constants.FILE_TYPE, FileType.GTFS.name());
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, "rut/file_name");
        headers.put(Constants.JOB_STATUS_ROUTING_DESTINATION, "mock:destination");
        headers.put(Constants.JOB_STATUS_JOB_TYPE, JobEvent.TimetableAction.IMPORT.name());
        headers.put(Constants.GENERATE_MAP_MATCHING, ImportGenerateMapMatching.AIR.name());

        addImportParametersTemplate.sendBodyAndHeaders(null, headers);

        sendImportJobRequest.expectedMessageCount(1);
        sendImportJobRequest.assertIsSatisfied();
        List<Exchange> receivedExchange = sendImportJobRequest.getExchanges();
        assertThat(receivedExchange).isNotEmpty().hasSize(1);
        Exchange actualExchange = receivedExchange.get(0);
        assertThat(actualExchange).isNotNull();
        Object header = actualExchange.getIn().getHeader(JSON_PART);
        assertThat(header).isNotNull().isInstanceOf(String.class);
        String headerValue = (String) header;
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> parametersMap = mapper.readValue(headerValue, Map.class);
        assertThat(parametersMap).containsKey(PARAMETERS);
        Map<String, Object> importParameters = (Map<String, Object>) parametersMap.get(PARAMETERS);
        assertThat(importParameters).isNotEmpty().containsKey(GTFS_IMPORT);
        Map<String, String> gtfsImport = (Map<String, String>) importParameters.get(GTFS_IMPORT);
        assertThat(gtfsImport).containsEntry("generate_map_matching", ImportGenerateMapMatching.AIR.name());
    }


}