package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers the generation of sspQuayAssociation.csv (scheduled stop point / quay association) in Chouette.
 * Only active when chouette.export.ssp.quay.association.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "chouette.export.ssp.quay.association.enabled", havingValue = "true")
public class ChouetteExportSspQuayAssociationRouteBuilder extends BaseRouteBuilder {

    private final String cronSchedule;

    private final String chouetteUrl;

    public ChouetteExportSspQuayAssociationRouteBuilder(
            @Value("${chouette.export.ssp.quay.association.cron.schedule:0+00+08+?+*+MON-FRI}") String cronSchedule,
            @Value("${chouette.url}") String chouetteUrl) {
        this.cronSchedule = cronSchedule;
        this.chouetteUrl = chouetteUrl;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://marduk/chouetteExportSspQuayAssociationQuartz?cron=" + cronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers export ScheduledStopPoint/Quay association")
                .to("direct:chouetteExportSspQuayAssociation")
                .routeId("chouette-export-ssp-quay-association-quartz");

        from("direct:chouetteExportSspQuayAssociation")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette export ScheduledStopPoint/Quay association")
                .removeHeaders("Camel*")
                .setBody(constant((Object) null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(chouetteUrl + "/chouette_iev/admin/export_ssp_quay_association")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette export ScheduledStopPoint/Quay association")
                .routeId("chouette-export-ssp-quay-association");
    }
}
