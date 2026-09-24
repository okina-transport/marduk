package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calls Chouette to generate the translation mapping once, on start-up.
 */
@Component
public class ChouetteGenerateTranslationMappingRouteBuilder extends BaseRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.generateTranslationMapping.enabled:true}")
    private boolean enabled;

    @Value("${chouette.generateTranslationMapping.startDelayMs:5000}")
    private int startDelayMs;

    @Value("${chouette.generateTranslationMapping.maxRetries:5}")
    private int maxRetries;

    @Value("${chouette.generateTranslationMapping.retryDelayMs:5000}")
    private long retryDelayMs;

    @Override
    public void configure() throws Exception {
        if (!enabled) {
            return;
        }

        super.configure();

        // Simple trigger firing only once, shortly after start-up
        singletonFrom("quartz://marduk/chouetteGenerateTranslationMappingQuartz?trigger.repeatCount=0&trigger.repeatInterval=1&triggerStartDelay=" + startDelayMs)
                .log(LoggingLevel.INFO, "Quartz triggers Chouette GenerateTranslationMapping on start-up")
                .to("direct:chouetteGenerateTranslationMapping")
                .routeId("chouette-GenerateTranslationMapping-quartz");

        from("direct:chouetteGenerateTranslationMapping")
                .onException(Exception.class)
                    .maximumRedeliveries(maxRetries)
                    .redeliveryDelay(retryDelayMs)
                    .retryAttemptedLogLevel(LoggingLevel.WARN)
                    .log(LoggingLevel.ERROR, correlation() + "Chouette GenerateTranslationMapping failed after " + maxRetries + " retries: ${exception.message}")
                .end()
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette GenerateTranslationMapping")
                .removeHeaders("Camel*")
                .setBody(constant((Object) null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/generate_translation_mapping")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette GenerateTranslationMapping")
                .routeId("chouette-GenerateTranslationMapping");
    }
}
