package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChouetteExportLineAndRouteIdsRouteBuilder extends BaseRouteBuilder {

    @Value("${chouette.export.line.and.route.ids.cron.schedule:0+00+06+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${ineo.sic.enabled:false}")
    private boolean isSicEnabled;

    @Override
    public void configure() throws Exception {
        if (!isSicEnabled) {
            return;
        }

        super.configure();

        singletonFrom("quartz2://marduk/chouetteExportLineAndRouteIdsQuartz?cron=" + cronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .autoStartup("{{chouette.ExportLineAndRouteIds.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers Chouette export line and route ids")
                .to("direct:chouetteExportLineAndRouteIds")
                .routeId("chouette-export-line-route-ids-quartz");

        from("direct:chouetteExportLineAndRouteIds")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette export line and route ids")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/export_line_and_route_ids")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette export line and route ids")
                .routeId("chouette-export-line-route-ids");
    }
}
