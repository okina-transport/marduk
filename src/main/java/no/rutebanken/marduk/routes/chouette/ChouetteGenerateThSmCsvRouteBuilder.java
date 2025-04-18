package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChouetteGenerateThSmCsvRouteBuilder extends BaseRouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ChouetteGenerateThSmCsvRouteBuilder.class);

    @Value("${chouette.generate-siri-from-th-csv.schedule:0+00+04+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.generate-siri-from-th-csv.enabled:false}")
    private boolean enabled;

    @Override
    public void configure() throws Exception {
        if (!enabled) {
            LOG.info("Siri SM generation from theoretical data is disabled");
            return;
        }

        if (StringUtils.isBlank(chouetteUrl)) {
            LOG.error("Chouette url is not set for Siri SM generation from theoretical data");
            return;
        }

        super.configure();

        singletonFrom("quartz2://marduk/chouetteGenerateTheoreticalSiriSmCsvQuartz?cron=" + cronSchedule + "&trigger" +
                ".timeZone=" + Constants.TIME_ZONE)
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers Chouette GenerateTheoreticalSiriSmCsv")
                .to("direct:chouetteGenerateTheoreticalSiriSmCsv")
                .routeId("chouette-GenerateTheoreticalSiriSmCsv-quartz");

        from("direct:chouetteGenerateTheoreticalSiriSmCsv")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette GenerateTheoreticalSiriSmCsv")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/generate_theoretical_stop_monitoring")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette GenerateTheoreticalSiriSmCsv")
                .routeId("chouette-GenerateTheoreticalSiriSmCsv");
    }
}
