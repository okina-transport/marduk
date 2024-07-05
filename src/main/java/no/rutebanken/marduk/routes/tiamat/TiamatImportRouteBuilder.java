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

package no.rutebanken.marduk.routes.tiamat;

import com.google.common.base.Strings;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.Utils.ImportRouteBuilder;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.chouette.CreateMail;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RawImportParameters;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import static no.rutebanken.marduk.Constants.*;

import no.rutebanken.marduk.security.TokenService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Utils.Utils.getHttp4;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Submits files to Chouette
 */
@Component
public class TiamatImportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Autowired
    CreateMail createMail;

    @Autowired
    ImportConfigurationDAO importConfigurationDAO;

    @Autowired
    TokenService tokenService;

    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:TiamatImportQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, correlation() + "Starting Tiamat import")
                .removeHeader(JOB_ID)
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.PENDING).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to("direct:updateStatus")
                .to("direct:getBlob")
                .choice()
                    .when(body().isNull())
                        .log(LoggingLevel.WARN, correlation() + "Import failed because blob could not be found")
                        .process(e-> {
                            if(TimetableAction.IMPORT.equals(ImportRouteBuilder.getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                ImportRouteBuilder.updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, ImportRouteBuilder.getTimeTableAction(e), false);
                            }
                        })
                    .otherwise()
                        .process(e -> {
                            Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                            e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
                            e.getIn().setHeader(ENABLE_VALIDATION, provider.chouetteInfo.enableValidation);
                        })
                        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                        .to("direct:tiamatAddImportParameters")
                .end()
                .routeId("tiamat-import-dataspace");

        from("direct:tiamatAddImportParameters")
                .process(e -> {
                    String fileName = e.getIn().getHeader(FILE_NAME, String.class);
                    String fileType = e.getIn().getHeader(FILE_TYPE, String.class);
                    Long providerId = e.getIn().getHeader(PROVIDER_ID, Long.class);
                    String user = e.getIn().getHeader(USER, String.class);
                    RawImportParameters rawImportParameters = new RawImportParameters();

                    rawImportParameters.setFileName(fileName);
                    rawImportParameters.setFileType(fileType);
                    rawImportParameters.setProviderId(providerId);
                    rawImportParameters.setUser(user);

                    e.getIn().setHeader(JSON_PART, getStringImportParameters(rawImportParameters));
                }) //Using header to addToExchange json data
                .log(LoggingLevel.DEBUG, correlation() + "import parameters: " + header(JSON_PART))
                .to("direct:tiamatSendImportJobRequest")
                .routeId("tiamat-import-addToExchange-parameters");

        from("direct:tiamatSendImportJobRequest")
                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data; charset=UTF-8"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, "tiamatUrl: " + tiamatUrl)
                .choice()
                    .when(header(IMPORT_TYPE).isEqualTo(FileType.NETEX_PARKING.name()))
                        .setProperty("tiamat_url", simple(tiamatUrl + "/parkings_netex_import_xml"))
                    .when(header(IMPORT_TYPE).isEqualTo(FileType.NETEX_POI.name()))
                        .setProperty("tiamat_url", simple(tiamatUrl + "/poi_netex_import_xml"))
                    .when(header(IMPORT_TYPE).isEqualTo(FileType.NETEX_STOP_PLACE.name()))
                        .setProperty("tiamat_url", simple(tiamatUrl + "/stop_places_netex_import_xml"))
                .end()
                .log(LoggingLevel.DEBUG, correlation() + "Calling Tiamat with URL: ${exchangeProperty.tiamat_url}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                // Attempt to retrigger delivery in case of errors
                .process(exchange -> {
                    String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
                    if (Strings.isNullOrEmpty(fileName)) {
                        throw new IllegalArgumentException("No file handle");
                    }
                    InputStream inputStream = exchange.getIn().getBody(InputStream.class);
                    if (inputStream == null) {
                        throw new IllegalArgumentException("No data");
                    }

                    MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
                    entityBuilder.addBinaryBody("file", inputStream, ContentType.DEFAULT_BINARY, fileName);
                    entityBuilder.addTextBody("file_name", exchange.getIn().getHeader(FILE_NAME, String.class));
                    entityBuilder.addTextBody("provider", exchange.getIn().getHeader(PROVIDER_ID, String.class));
                    entityBuilder.addTextBody("folder", exchange.getIn().getHeader(FOLDER_NAME, String.class));

                    if (exchange.getIn().getHeader(IMPORT_TYPE)==FileType.NETEX_STOP_PLACE.name()) {
                        entityBuilder.addTextBody("containsMobiitiIds", String.valueOf(true));
                    }

                    exchange.getOut().setBody(entityBuilder.build());
                    exchange.getOut().setHeaders(exchange.getIn().getHeaders());

                    if (exchange.getIn().getHeader("Authorization") == null) {
                        exchange.getOut().setHeader("Authorization", "Bearer " + tokenService.getToken());
                    }
                })
                .process(e -> {
                    String url = e.getProperty("tiamat_url", String.class);
                    url = url.replace("http://", "http4://");
                    e.setProperty("tiamat_url", url);
                })
                .toD("${exchangeProperty.tiamat_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(Constants.JOB_STATUS_URL, getHttp4(e.getIn().getHeader("Location", String.class)));
                    e.getIn().setHeader(Constants.JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:tiamatProcessImportResult"))
                .setHeader(JOB_STATUS_JOB_TYPE, constant(TimetableAction.IMPORT_NETEX.name()))
                .removeHeader("loopCounter")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.STARTED).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to("direct:updateStatus")
                .to("activemq:queue:TiamatPollStatusQueue")
                .routeId("tiamat-send-import-job");


        from("direct:tiamatProcessImportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setBody(constant(""))
                .choice()
                    //import ok
                    .when(PredicateBuilder.and(constant("false").isEqualTo(header(ENABLE_VALIDATION)), simple("${header.action_report_result} == 'OK'")))
                        .to("direct:tiamatCheckScheduledJobsBeforeTriggeringNextAction")                 //import ok
                    .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'"))
                        .to("direct:tiamatCheckScheduledJobsBeforeTriggeringNextAction")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.OK).build())
                    //import ko
                    .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'NOK'"))
                        .log(LoggingLevel.INFO, correlation() + "Import ok but validation failed")
                        .process(e -> {
                            if(TimetableAction.IMPORT.equals(ImportRouteBuilder.getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                ImportRouteBuilder.updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, ImportRouteBuilder.getTimeTableAction(e), false);
                            }
                        })
                    //import ko
                    .when(simple("${header.action_report_result} == 'NOK'"))
                        .log(LoggingLevel.WARN, correlation() + "Import not ok")
                        .process(e -> {
                            if(TimetableAction.IMPORT.equals(ImportRouteBuilder.getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                ImportRouteBuilder.updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, ImportRouteBuilder.getTimeTableAction(e), false);
                            }

                        })
                    //import ko
                    .otherwise()
                        .log(LoggingLevel.ERROR, correlation() + "Something went wrong on import")
                        .process(e -> {
                            if(TimetableAction.IMPORT.equals(ImportRouteBuilder.getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                ImportRouteBuilder.updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, ImportRouteBuilder.getTimeTableAction(e), false);
                            }
                        })
                .end()
                .to("direct:updateStatus")
                .routeId("timat-process-import-status");

        // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
        from("direct:tiamatCheckScheduledJobsBeforeTriggeringNextAction")
                .setProperty("job_status_url", simple(tiamatUrl + "/jobs/${header.\" + CHOUETTE_REFERENTIAL + \"}?timetableAction=importer&status=SCHEDULED&status=STARTED"))
                .toD("${exchangeProperty.job_status_url}")
                .choice()
                    .when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
                        .log(LoggingLevel.INFO, correlation() + "Import ok, skipping next step as there are more import jobs active")
                    .otherwise()
                        .log(LoggingLevel.INFO, correlation() + "Import ok, triggering next step.")
                        .setBody(constant(""))
                        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                        .log(LoggingLevel.INFO, correlation() + "Import ok")
                        .to("activemq:queue:TiamatPollStatusQueue")
                .end()
                .routeId("tiamat-process-job-list-after-import");
    }

    private String getStringImportParameters(RawImportParameters rawImportParameters) {
        rawImportParameters.setProvider(getProviderRepository().getProvider(rawImportParameters.getProviderId()));
        return Parameters.createStringImportParameters(rawImportParameters);
    }
}


