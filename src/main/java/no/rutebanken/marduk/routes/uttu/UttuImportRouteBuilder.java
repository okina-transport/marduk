package no.rutebanken.marduk.routes.uttu;

import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.chouette.CreateMail;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.GTFS_FLEX_ONLY;
import static no.rutebanken.marduk.Constants.JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.UTTU_IMPORT_STATUS;
import static no.rutebanken.marduk.Constants.WORKLOW;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;

/**
 * Submits files to Uttu
 */
@Component
public class UttuImportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    CreateMail createMail;

    public UttuImportRouteBuilder() {}

    private static String gtfsFlexJobType(org.apache.camel.Exchange e) {
        return Boolean.TRUE.equals(e.getIn().getHeader(GTFS_FLEX_ONLY, Boolean.class))
                ? JobEvent.GTFS_FULL_FLEX_TYPE
                : String.valueOf(FileType.GTFS_FLEX);
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("jms:queue:GtfsFlexUttuPredefinedImport")
                .routeId("GtfsFlexUttuPredefinedImport")
                .streamCache(Boolean.TRUE)
                .transacted()
                .process(e -> {
                    JobEvent.providerJobBuilder(e)
                            .timetableAction(TimetableAction.IMPORT_UTTU)
                            .state(State.PENDING)
                            .type(gtfsFlexJobType(e))
                            .build();
                })
                .to(ROUTE_UPDATE_STATUS)
                .to("jms:queue:importGtfsFlexUttuQueue")
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:processUttuGtfsFlexImportResult"))
                .setHeader(JOB_STATUS_JOB_TYPE, constant(TimetableAction.IMPORT_UTTU.name()))
                .removeHeader("loopCounter")
                .to("jms:queue:UttuPollStatusQueue")
                .end();

        from("direct:processUttuGtfsFlexImportResult")
                .choice()
                    .when(header(UTTU_IMPORT_STATUS).isEqualTo("OK"))
                        .log(LoggingLevel.INFO,"GTFS flex import completed successfully")
                        .to("direct:terminateGtfsFlexUttuImport")
                    .otherwise()
                        .log(LoggingLevel.ERROR,"Error on GTFS flex import")
                        .to("direct:handleGtfsFlexImportERROR")
                .endChoice()
                .end()
                .routeId("process-uttu-gtfs-flex-import-result");

        from("direct:terminateGtfsFlexUttuImport")
                .process(e -> {
                    JobEvent.providerJobBuilder(e)
                            .timetableAction(TimetableAction.IMPORT_UTTU)
                            .state(State.OK)
                            .type(gtfsFlexJobType(e))
                            .build();

                    if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                        createMail.createMail(e, "GTFS", TimetableAction.IMPORT, true);
                    }
                })
                .to(ROUTE_UPDATE_STATUS)
                .routeId("terminate-gtfs-flex-uttu-import");

        from("direct:handleGtfsFlexImportERROR")
                .log(LoggingLevel.ERROR, correlation() + "Something went wrong on import")
                .process(e -> {
                    JobEvent.providerJobBuilder(e)
                            .timetableAction(TimetableAction.IMPORT_UTTU)
                            .state(State.FAILED)
                            .type(gtfsFlexJobType(e))
                            .build();

                    if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                        createMail.createMail(e, "GTFS", TimetableAction.IMPORT, false);
                    }
                })
                .to(ROUTE_UPDATE_STATUS)
                .routeId("handle-gtfs-flex-uttu-import-error");
    }
}