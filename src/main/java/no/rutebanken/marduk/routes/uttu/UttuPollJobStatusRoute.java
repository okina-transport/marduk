package no.rutebanken.marduk.routes.uttu;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.uttu.json.UttuJobStatus;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.support.builder.PredicateBuilder.or;

@Component
public class UttuPollJobStatusRoute extends AbstractChouetteRouteBuilder {

    @Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${uttu.url}")
    private String uttuUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("jms:queue:UttuPollStatusQueue?transacted=true")
                .transacted()
                .validate(header(CORRELATION_ID).isNotNull())
                .validate(header(JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .validate(header(JOB_STATUS_JOB_TYPE).isNotNull())
                .to("direct:uttuCheckJobStatus")
                .routeId("uttu-validate-job-status-parameters");

        from("direct:uttuCheckJobStatus")
                .process(e -> e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1))
                .process(e -> {
                    String correlationId = e.getIn().getHeader(CORRELATION_ID, String.class);
                    URL url = new URL(uttuUrl + "/job/correlation/" + correlationId);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Accept", "application/json");
                    int responseCode = con.getResponseCode();
                    if (responseCode == 404) {
                        e.setProperty("uttuJobStatus", "PENDING");
                    } else if (responseCode >= 200 && responseCode < 300) {
                        String body;
                        try (InputStream is = con.getInputStream()) {
                            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        UttuJobStatus status = new ObjectMapper().readValue(body, UttuJobStatus.class);
                        if (status.getId() != null) {
                            e.getIn().setHeader(JOB_ID, status.getId());
                        }
                        e.setProperty("uttuJobStatus", status.getStatus());
                    } else {
                        throw new IOException("Unexpected HTTP status " + responseCode + " from Uttu (" + url + ")");
                    }
                })
                .choice()
                    .when(or(simple("${exchangeProperty.uttuJobStatus} == 'OK'"), simple("${exchangeProperty.uttuJobStatus} == 'FAILED'")))
                        .setHeader(UTTU_IMPORT_STATUS, simple("${exchangeProperty.uttuJobStatus}"))
                        .toD("${header." + JOB_STATUS_ROUTING_DESTINATION + "}")
                    .when(simple("${header.loopCounter} > " + maxRetries))
                        .log(LoggingLevel.WARN, correlation() + "Uttu GTFS-Flex import job timed out. Stopping.")
                        .setHeader(UTTU_IMPORT_STATUS, constant("FAILED"))
                        .toD("${header." + JOB_STATUS_ROUTING_DESTINATION + "}")
                    .otherwise()
                        .to("direct:uttuRescheduleJob")
                .end()
                .routeId("uttu-get-job-status");

        from("direct:uttuRescheduleJob")
                .removeHeader("scheduledJobId")
                .setBody(constant(""))
                .delay(retryDelay)
                .to("jms:queue:UttuPollStatusQueue")
                .routeId("uttu-reschedule-job");
    }
}
