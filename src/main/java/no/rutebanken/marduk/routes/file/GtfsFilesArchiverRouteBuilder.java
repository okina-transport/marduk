package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.JobResponseWithLinks;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

import static no.rutebanken.marduk.Constants.*;

@Component
public class GtfsFilesArchiverRouteBuilder extends BaseRouteBuilder {

    @Autowired
    private GtfsFilesArchiver gtfsFilesArchiver;

    @Autowired
    private FileSystemService fileSystemService;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:archiveTripsAndStopTimes")
                .log(LoggingLevel.INFO, correlation() + "archive trips.txt and stop_times.txt from GTFS zip")
                .validate(body().isInstanceOf(JobResponseWithLinks.class))
                .validate(header(CLEAN_MODE).isNotNull())
                .process(e -> {
                            JobResponseWithLinks body = e.getIn().getBody(JobResponseWithLinks.class);
                            String cleanMode = e.getIn().getHeader(CLEAN_MODE, String.class);
                            File gtfsZipFile = fileSystemService.getImportZipFileByReferentialAndJobId(body.referential, String.valueOf(body.getId()));
                            if ("purge".equals(cleanMode)) {
                                gtfsFilesArchiver.cleanOrganisationStopTimes(body.referential);
                                gtfsFilesArchiver.cleanOrganisationTrips(body.referential);
                            }
                            gtfsFilesArchiver.archiveTripsAndStopTimes(gtfsZipFile, body.referential);
                        })
                .log(LoggingLevel.INFO, correlation() + "trips.txt and stop_times.txt have been successfully archived")
                .routeId("archive-trips-and-stop-times");

    }
}
