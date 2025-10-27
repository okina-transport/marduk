package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChouetteGenerateIneoVJMappingCsvRouteBuilder extends BaseRouteBuilder {

    @Value("${chouette.generateVjMappingCsv.schedule:0+0+1+?+*+*}")
    private String cronSchedule;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.generateVjMappingCsv:false}")
    private boolean enabled;

    @Override
    public void configure() throws Exception {
        if (!enabled) {
            return;
        }

        super.configure();

        singletonFrom("quartz://marduk/chouetteGenerateIneoVJMappingCsvQuartz?cron=" + cronSchedule + "&trigger" +
                ".timeZone=" + Constants.TIME_ZONE)
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers Chouette GenerateIneoVJMappingCsv")
                .to("direct:chouetteGenerateIneoVJMappingCsv")
                .routeId("chouette-GenerateIneoVJMappingCsv-quartz");

        from("direct:chouetteGenerateIneoVJMappingCsv")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette GenerateIneoVJMappingCsv")
                .removeHeaders("Camel*")
                .setBody(constant((Object) null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/generate_ineo_vj_mapping")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette GenerateIneoVJMappingCsv")
                .routeId("chouette-GenerateIneoVJMappingCsv");
    }
}
