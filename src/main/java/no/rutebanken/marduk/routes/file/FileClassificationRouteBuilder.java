package no.rutebanken.marduk.routes.file;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

/**
 * Receives file handle, pulls file from blob store, classifies files and performs initial validation.
 */
@Component
public class FileClassificationRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.INFO, correlation() + "Could not process file ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .setBody(simple(""))      //remove file data from body
                .to("activemq:queue:DeadLetterQueue");


        from("google-pubsub:{{blobstore.gcs.project.id}}:ProcessFileQueue?"
                     + "maxMessagesPerPoll=1&"
                     + "concurrentConsumers=10")
                // TODO jms transaction
                .doTry()
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.STARTED).build()).to("direct:updateStatus")
                .to("direct:getBlob")
                .convertBodyTo(byte[].class)
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.ZIP_WITH_SINGLE_FOLDER.name()))
                .log(LoggingLevel.WARN, correlation() + "Unexpected file type or invalid file ${header." + FILE_HANDLE + "}")
                .to("direct:repackZipFile")
                .when(header(FILE_TYPE).isEqualTo(FileType.RAR.name()))
                .log(LoggingLevel.INFO, correlation() + "Splitting and repackaging file ${header." + FILE_HANDLE + "}")
                .to("direct:splitRarFile")
                .otherwise()
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.GTFS.name()))
                .log(LoggingLevel.INFO, correlation() + "Transforming GTFS file ${header." + FILE_HANDLE + "}")
                .to("direct:transformGtfsFile")
                .end()
                .log(LoggingLevel.INFO, correlation() + "Posting " + FILE_HANDLE + " ${header." + FILE_HANDLE + "} and " + FILE_TYPE + " ${header." + FILE_TYPE + "} on chouette import queue.")
                .setBody(simple(""))   //remove file data from body since this is in blobstore
                .to("google-pubsub:{{blobstore.gcs.project.id}}:ChouetteImportQueue")
                .end()
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .to("direct:getBlob")
                .routeId("file-classify");


        from("direct:transformGtfsFile")
                .choice().when(simple("{{gtfs.transform.skip:false}}"))
                .log(LoggingLevel.INFO, getClass().getName(), "Skipping gtfs transformation for ${header." + FILE_HANDLE + "}")
                .otherwise()
                .bean(method(ZipFileUtils.class, "transformGtfsFile"))
                .log(LoggingLevel.INFO, correlation() + "ZIP-file transformed ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .endChoice()
                .routeId("file-transform-gtfs");

        from("direct:repackZipFile")
                .bean(method(ZipFileUtils.class, "rePackZipFile"))
                .log(LoggingLevel.INFO, correlation() + "ZIP-file repacked ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .to("google-pubsub:{{blobstore.gcs.project.id}}:ProcessFileQueue")
                .routeId("file-repack-zip");

        from("direct:splitRarFile")
                .split(method(RARToZipFilesSplitter.class, "splitRarFile"))
                .process(e -> {
                    int currentPart = e.getProperty("CamelSplitIndex", Integer.class) + 1;
                    String currentPartPadded = StringUtils.leftPad("" + currentPart, 4, '0');
                    String numParts = e.getProperty("CamelSplitSize", String.class);
                    e.getIn().setHeader(FILE_HANDLE, e.getIn().getHeader(FILE_HANDLE) + "_part_" + currentPartPadded + "_of_" + numParts + ".zip");
                    e.getIn().setHeader(FILE_NAME, e.getIn().getHeader(FILE_NAME) + "_part_" + currentPartPadded + "_of_" + numParts + ".zip");
                    e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString());
                })
                .log(LoggingLevel.INFO, correlation() + "New fragment from RAR file ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .to("google-pubsub:{{blobstore.gcs.project.id}}:ProcessFileQueue")
                .routeId("file-split-rar");

    }
}
