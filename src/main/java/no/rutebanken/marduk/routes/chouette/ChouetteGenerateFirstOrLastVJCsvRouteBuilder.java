package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChouetteGenerateFirstOrLastVJCsvRouteBuilder extends BaseRouteBuilder {

    @Value("${chouette.generateFirstOrLastVJCsv.schedule:0+00+04+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.generateFirstOrLastCsv:false}")
    private boolean enabled;

    @Override
    public void configure() throws Exception {
        if (!enabled) {
            return;
        }

        super.configure();

        singletonFrom("quartz2://marduk/chouetteGenerateFirstOrLastVJCsvQuartz?cron=" + cronSchedule + "&trigger" +
                ".timeZone=" + Constants.TIME_ZONE)
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers Chouette GenerateFirstOrLastVJCsv")
                .to("direct:chouetteGenerateFirstOrLastVJCsv")
                .routeId("chouette-GenerateFirstOrLastVJCsv-quartz");

        from("direct:chouetteGenerateFirstOrLastVJCsv")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette GenerateFirstOrLastVJCsv")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/generate_first_or_last_journey_info")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette GenerateFirstOrLastVJCsv")
                .routeId("chouette-GenerateFirstOrLastVJCsv");
    }
}
