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

        from("direct:archiveGtfsData")
                .log(LoggingLevel.INFO, correlation() + "archive, stop_times.txt, calendar.txt and calendar_dates.txt from GTFS zip")
                .validate(body().isInstanceOf(JobResponseWithLinks.class))
                .validate(header(CLEAN_MODE).isNotNull())
                .process(e -> {
                            JobResponseWithLinks body = e.getIn().getBody(JobResponseWithLinks.class);
                            String cleanMode = e.getIn().getHeader(CLEAN_MODE, String.class);
                            File gtfsZipFile = fileSystemService.getImportZipFileByReferentialAndJobId(body.referential, String.valueOf(body.getId()));
                            if ("purge".equals(cleanMode)) {
                                gtfsFilesArchiver.cleanOrganisationArchivedFiles(body.referential, "stop_times");
                                gtfsFilesArchiver.cleanOrganisationArchivedFiles(body.referential, "trips");
                                gtfsFilesArchiver.cleanOrganisationArchivedFiles(body.referential, "calendar");
                                gtfsFilesArchiver.cleanOrganisationArchivedFiles(body.referential, "calendar_dates");

                            }
                            gtfsFilesArchiver.archiveGtfsData(gtfsZipFile, body.referential);
                        })
                .log(LoggingLevel.INFO, correlation() + "trips.txt, stop_times.txt, calendar.txt and calendar_dates.txt have been successfully archived")
                .routeId("archive-gtfs-data");

    }
}
