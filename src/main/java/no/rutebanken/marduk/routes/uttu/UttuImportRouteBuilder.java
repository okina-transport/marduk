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

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.*;

/**
 * Submits files to Uttu
 */
@Component
public class UttuImportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    CreateMail createMail;

    public UttuImportRouteBuilder() {}

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("jms:queue:GtfsFlexUttuPredefinedImport")
                .routeId("GtfsFlexUttuPredefinedImport")
                .streamCache(Boolean.TRUE)
                .transacted()
                .process(e -> {
                    JobEvent.providerJobBuilder(e)
                            .timetableAction(TimetableAction.IMPORT)
                            .state(State.PENDING)
                            .type(String.valueOf(FileType.GTFS_FLEX))
                            .build();
                })
                .to(ROUTE_UPDATE_STATUS)
                .to("jms:queue:importGtfsFlexUttuQueue")
                .end();

        from("jms:queue:importGtfsFlexUttuCompleted")
                .choice()
                    .when(header(UTTU_IMPORT_STATUS).isEqualTo("OK"))
                        .log(LoggingLevel.INFO,"GTFS flex import completed successfully")
                        .to("direct:terminateGtfsFlexUttuImport")
                    .otherwise()
                        .log(LoggingLevel.ERROR,"Error on GTFS flex import")
                        .to("direct:handleGtfsFlexImportERROR")
                .endChoice()
                .end()
                .routeId("import-gtfs-flex-uttu-completed");

        from("direct:terminateGtfsFlexUttuImport")
                .process(e -> {
                    JobEvent.providerJobBuilder(e)
                            .timetableAction(TimetableAction.IMPORT)
                            .state(State.OK)
                            .type(String.valueOf(FileType.GTFS_FLEX))
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
                            .timetableAction(TimetableAction.IMPORT)
                            .state(State.FAILED)
                            .type(String.valueOf(FileType.GTFS_FLEX))
                            .build();

                    if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                        createMail.createMail(e, "GTFS", TimetableAction.IMPORT, false);
                    }
                })
                .to(ROUTE_UPDATE_STATUS)
                .routeId("handle-gtfs-flex-uttu-import-error");
    }
}