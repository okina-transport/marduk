package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChouetteExportLineIdsRouteBuilder  extends BaseRouteBuilder {

    @Value("${chouette.export.line.ids.cron.schedule:0+00+08+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/chouetteExportLineIdsQuartz?cron=" + cronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .autoStartup("{{chouette.exportLineIds.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers export Line ids")
                .to("direct:chouetteExportLineIds")
                .routeId("chouette-export-line-ids-quartz");

        from("direct:chouetteExportLineIds")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette exportLineIds")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/export_lines_ids")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette export line ids")
                .routeId("chouette-export-line-ids");
    }
}
