/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.blobstore.BlobStoreRoute;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItem;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.IMPORT;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Upload file to blob store and trigger import pipeline.
 */
@Component
public class FileUploadRouteBuilder extends BaseRouteBuilder {

    private static final String FILE_CONTENT_HEADER = "RutebankenFileContent";

    @Override
    public void configure() throws Exception {
        super.configure();


        from("direct:uploadFilesAndStartImport")
                .process(FileInformations::getObjectUpload)
                .split().body()
                .to("direct:importLaunch")
                .routeId("files-upload");


        from("direct:uploadFileAndStartImport")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED).build()).inOnly("direct:updateStatus")
                .doTry()
                .log(LoggingLevel.INFO, correlation() + "About to upload timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(header(FILE_CONTENT_HEADER))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, correlation() + "Finished uploading timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(constant(null))
                .inOnly("activemq:queue:ProcessFileQueue")
                .log(LoggingLevel.INFO, correlation() + "Triggered import pipeline for timetable file: ${header." + FILE_HANDLE + "}")
                .doCatch(Exception.class)
                .log(LoggingLevel.WARN, correlation() + "Upload of timetable data to blob store failed for file: ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED).build()).inOnly("direct:updateStatus")
                .end()
                .routeId("file-upload-and-start-import");

        from("direct:importLaunch")
                .process(e -> e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString())))
                .setHeader(FILE_NAME, simple("${body.name}"))
                .setHeader(FILE_HANDLE, simple("inbound/received/${header." + CHOUETTE_REFERENTIAL + "}/${header." + FILE_NAME + "}"))
                .process(e -> {
                    Long providerId = e.getIn().getHeader(PROVIDER_ID, Long.class);
                    Provider provider = getProviderRepository().getNonMobiitiProvider(providerId)
                            .orElseThrow(() -> new Exception("No provider found for import with id " + providerId));
                    DiskFileItem fileItem = (DiskFileItem) e.getIn().getBody();
                    String importPath = BlobStoreRoute.importPath(provider) + "/" + fileItem.getName();
                    e.getIn().setHeader(FILE_HANDLE, importPath);
                })
                .setHeader(IMPORT, constant(true))
                .process(e -> e.getIn().setHeader(FILE_CONTENT_HEADER, new CloseShieldInputStream(e.getIn().getBody(FileItem.class).getInputStream())))
                .to("direct:uploadFileAndStartImport")
                .routeId("importLaunch");
    }
}
