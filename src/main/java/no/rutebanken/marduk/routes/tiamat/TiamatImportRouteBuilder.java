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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;

import no.rutebanken.marduk.domain.ConfigurationFtp;
import no.rutebanken.marduk.domain.ConfigurationUrl;
import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.chouette.CreateMail;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RawImportParameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;

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
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.Utils.getHttp4;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Submits files to Chouette
 */
@Component
public class TiamatImportRouteBuilder extends AbstractChouetteRouteBuilder {


    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${client.name}")
    private String client;

    @Value("${server.name}")
    private String server;

    @Autowired
    CreateMail createMail;

    @Autowired
    ImportConfigurationDAO importConfigurationDAO;

    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:TiamatImportQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, correlation() + "Starting Tiamat import")
                .removeHeader(JOB_ID)
                .process(e -> {
                    Boolean analyze = e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null ? e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) : false;
                    TimetableAction action = analyze ? TimetableAction.FILE_ANALYZE : TimetableAction.IMPORT;
                    JobEvent.providerJobBuilder(e).timetableAction(action).state(State.PENDING).type(e.getIn().getHeader(FILE_TYPE, String.class)).build();
                })
                .to("direct:updateStatus")
                .to("direct:getBlob")
                .choice()
                    .when(body().isNull())
                        .log(LoggingLevel.WARN, correlation() + "Import failed because blob could not be found")
                        .process(e-> {
                            if(TimetableAction.IMPORT.equals(getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, getTimeTableAction(e), false);
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
                .setProperty("tiamat_url", simple(tiamatUrl + "/parkings_netex_import_xml"))
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
                    entityBuilder.addTextBody("user", exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
                    entityBuilder.addTextBody("provider", exchange.getIn().getHeader(PROVIDER_ID, String.class));

                    exchange.getOut().setBody(entityBuilder.build());
                    exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                })
                .toD("${exchangeProperty.tiamat_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(JOB_STATUS_URL, getHttp4(e.getIn().getHeader("Location", String.class)));
                    e.getIn().setHeader(JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:tiamatProcessImportResult"))
                .choice()
                    .when(simple("${header." + ANALYZE_ACTION + "}"))
                        .setHeader(JOB_STATUS_JOB_TYPE, constant(TimetableAction.FILE_ANALYZE.name()))
                    .otherwise()
                        .setHeader(JOB_STATUS_JOB_TYPE, constant(TimetableAction.IMPORT.name()))
                    .end()
                .removeHeader("loopCounter")
                .to("activemq:queue:TiamatPollStatusQueue")
                .routeId("tiamat-send-import-job");


        from("direct:tiamatProcessImportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setBody(constant(""))
                .process(e -> {
                    String toto = e.getIn().getHeader(ENABLE_VALIDATION, String.class);
                })
                .choice()
                    //import ok
                    .when(PredicateBuilder.and(constant("false").isEqualTo(header(ENABLE_VALIDATION)), simple("${header.action_report_result} == 'OK'")))
//                        .to("direct:tiamatCheckScheduledJobsBeforeTriggeringNextAction")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.OK).build();
                        })
                    //import ok
                    .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'"))
//                        .to("direct:tiamatCheckScheduledJobsBeforeTriggeringNextAction")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.OK).build();
                        })
                    //import ko
                    .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'NOK'"))
                        .log(LoggingLevel.INFO, correlation() + "Import ok but validation failed")
                        .process(e -> {
                            if(TimetableAction.IMPORT.equals(getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, getTimeTableAction(e), false);
                            }
                        })
                    //import ko
                    .when(simple("${header.action_report_result} == 'NOK'"))
                        .log(LoggingLevel.WARN, correlation() + "Import not ok")
                        .process(e -> {
                            if(TimetableAction.IMPORT.equals(getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, getTimeTableAction(e), false);
                            }

                        })
                    //import ko
                    .otherwise()
                        .log(LoggingLevel.ERROR, correlation() + "Something went wrong on import")
                        .process(e -> {
                            if(TimetableAction.IMPORT.equals(getTimeTableAction(e)) && e.getIn().getHeader(IMPORT_CONFIGURATION_ID) != null){
                                updateLastTimestamp(e);
                            }
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                            if (e.getIn().getHeader(WORKLOW, String.class) != null) {
                                createMail.createMail(e, null, getTimeTableAction(e), false);
                            }
                        })
                .end()
                .to("direct:updateStatus")
                .routeId("timat-process-import-status");

        // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
        from("direct:tiamatCheckScheduledJobsBeforeTriggeringNextAction")
                .setProperty("job_status_url", simple(tiamatUrl + "/parkings_netex_import_xml"))
                .toD("${exchangeProperty.job_status_url}")
                .choice()
                    .when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
                        .log(LoggingLevel.INFO, correlation() + "Import and validation ok, skipping next step as there are more import jobs active")
                    .otherwise()
                        .log(LoggingLevel.INFO, correlation() + "Import and validation ok, triggering next step.")
                        .setBody(constant(""))
                        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                        .log(LoggingLevel.INFO, correlation() + "Import ok")
                        .to("activemq:queue:TiamatPollStatusQueue")
                .end()
                .routeId("tiamat-process-job-list-after-import");
    }

    private void updateLastTimestamp(Exchange e)throws JsonProcessingException {
        String referential = (String) e.getIn().getHeader(CHOUETTE_REFERENTIAL);
        String fileName = (String) e.getIn().getHeader(FILE_NAME);
        String importConfigurationId = (String) e.getIn().getHeader(IMPORT_CONFIGURATION_ID);

        ImportConfiguration importConfiguration = importConfigurationDAO.getImportConfiguration(referential, importConfigurationId);

        // Vérification pour les configurations FTP
        List<ConfigurationFtp> ftpList = importConfiguration.getConfigurationFtpList().isEmpty() ? new ArrayList<>() : importConfiguration.getConfigurationFtpList();
        updateLastTimestampIfFileNameMatchesForFtp(ftpList, fileName);

        // Vérification pour les configurations URL
        List<ConfigurationUrl> urlList = importConfiguration.getConfigurationUrlList();
        updateLastTimestampIfFileNameMatchesForUrl(urlList, fileName);

        importConfigurationDAO.update(referential, importConfiguration);
    }
        // Méthode pour mettre à jour lastTimestamp si le fichier correspond (pour les configurations FTP)
    private void updateLastTimestampIfFileNameMatchesForFtp(List<ConfigurationFtp> ftpList, String fileName) {
        ftpList.stream().filter(config -> config.getFilename().equals(fileName))
                .findFirst().ifPresent(config -> config.setLastTimestamp(null));
    }

    // Méthode pour mettre à jour lastTimestamp si le fichier correspond (pour les configurations URL)
    private void updateLastTimestampIfFileNameMatchesForUrl(List<ConfigurationUrl> urlList, String fileName) {
        urlList.stream().filter(config -> config.getUrl().substring(config.getUrl().lastIndexOf('/') + 1).equals(fileName))
            .findFirst().ifPresent(config -> config.setLastTimestamp(null));
    }

    private String getStringImportParameters(RawImportParameters rawImportParameters) {
        rawImportParameters.setProvider(getProviderRepository().getProvider(rawImportParameters.getProviderId()));
        return Parameters.createStringImportParameters(rawImportParameters);
    }

    private TimetableAction getTimeTableAction(Exchange e) {
        Boolean analyze = e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null ? e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) : false;
        return analyze ? TimetableAction.FILE_ANALYZE : TimetableAction.IMPORT;
    }

}


