package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.security.TokenService;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.ConnectException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

class ChouetteRemoveOldJobsRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:chouetteRemoveOldJobs")
    protected MockEndpoint chouetteRemoveOldJobs;

    @EndpointInject("mock:nabuRemoveOldJobs")
    protected MockEndpoint nabuRemoveOldJobs;

    @EndpointInject("mock:tiamatRemoveOldJobs")
    protected MockEndpoint tiamatRemoveOldJobs;

    @Produce("direct:chouetteRemoveOldJobs")
    protected ProducerTemplate removeOldJobsTemplate;

    @MockitoBean
    private TokenService tokenService;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${nabu.url}")
    private String nabuUrl;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @BeforeEach
    void beforeEach() throws Exception {
        when(tokenService.getAuthorizationHeader()).thenReturn("Bearer token");
        chouetteRemoveOldJobs.reset();
        nabuRemoveOldJobs.reset();
        tiamatRemoveOldJobs.reset();

        AdviceWith.adviceWith(context, "chouette-remove-old-jobs", a -> a.interceptSendToEndpoint(chouetteUrl + "/*")
                .skipSendToOriginalEndpoint()
                .to("mock:chouetteRemoveOldJobs"));
        AdviceWith.adviceWith(context, "nabu-remove-old-events", a -> a.interceptSendToEndpoint(nabuUrl + "/*")
                .skipSendToOriginalEndpoint()
                .to("mock:nabuRemoveOldJobs"));
        AdviceWith.adviceWith(context, "tiamat-remove-old-jobs", a -> a.interceptSendToEndpoint(tiamatUrl + "/*")
                .skipSendToOriginalEndpoint()
                .to("mock:tiamatRemoveOldJobs"));
        context.start();
    }

    @Test
    void removeOldJobsCallsChouetteThenNabuThenTiamat() throws Exception {
        chouetteRemoveOldJobs.expectedMessageCount(1);
        nabuRemoveOldJobs.expectedMessageCount(1);
        tiamatRemoveOldJobs.expectedMessageCount(1);
        tiamatRemoveOldJobs.expectedHeaderReceived("Authorization", "Bearer token");
        tiamatRemoveOldJobs.expectedHeaderReceived("keepJobs", 10);
        tiamatRemoveOldJobs.expectedHeaderReceived("keepDays", 20);

        removeOldJobsTemplate.sendBodyAndHeaders(null, Map.of("keepJobs", 10, "keepDays", 20));

        MockEndpoint.assertIsSatisfied(chouetteRemoveOldJobs, nabuRemoveOldJobs, tiamatRemoveOldJobs);
    }

    @Test
    void tiamatFailureDoesNotFailThePurge() throws Exception {
        chouetteRemoveOldJobs.expectedMessageCount(1);
        nabuRemoveOldJobs.expectedMessageCount(1);
        tiamatRemoveOldJobs.expectedMessageCount(1);
        tiamatRemoveOldJobs.whenAnyExchangeReceived(e -> {
            throw new ConnectException("Tiamat unreachable");
        });

        assertDoesNotThrow(() -> removeOldJobsTemplate.sendBody(null));

        MockEndpoint.assertIsSatisfied(chouetteRemoveOldJobs, nabuRemoveOldJobs, tiamatRemoveOldJobs);
    }
}
