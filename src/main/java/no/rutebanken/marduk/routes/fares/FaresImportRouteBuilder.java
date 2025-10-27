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

package no.rutebanken.marduk.routes.fares;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.utils.ImportRouteBuilder;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.chouette.CreateMail;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RawImportParameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import no.rutebanken.marduk.security.TokenService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.component.http.HttpMethods;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.Utils.getHttp4;
import static no.rutebanken.marduk.utils.Utils.getLastPathElementOfUrl;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;


@Component
public class FaresImportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${fares.url}")
    private String faresUrl;

    @Autowired
    CreateMail createMail;

    @Autowired
    TokenService tokenService;

    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();

        from("jms:queue:FaresImportQueue?transacted=true").streamCache(Boolean.TRUE)
                .transacted()
                .log(LoggingLevel.INFO, correlation() + "Starting Fares import")
                .removeHeader(JOB_ID)
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.PENDING).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to(ROUTE_UPDATE_STATUS)
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
                            e.getIn().setHeader(PROVIDER_ID, provider.getId());
                        })
                        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                        .to("direct:faresSendImportJobRequest")
                .end()
                .routeId("fares-import-dataspace");

        from("direct:faresSendImportJobRequest")
                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(exchange -> {
                    InputStream inputStream = exchange.getIn().getBody(InputStream.class);
                    String user = exchange.getIn().getHeader(USER, String.class);
                    String folder = exchange.getIn().getHeader(FOLDER_NAME, String.class);
                    String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);

                    if (fileName == null || inputStream == null || user == null || folder == null) {
                        throw new IllegalArgumentException("Missing required data: fileName, inputStream, folder or user");
                    }

                    MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                    multipartEntityBuilder.addBinaryBody("file", inputStream, ContentType.DEFAULT_BINARY, fileName);
                    multipartEntityBuilder.addTextBody("user", user);
                    multipartEntityBuilder.addTextBody("fileName", fileName);
                    multipartEntityBuilder.addTextBody("folder", folder);

                    Message message = exchange.getMessage();
                    message.setHeader("user", user);
                    message.setHeader("fileName", fileName);
                    message.setHeader("folder", folder);

                    HttpEntity httpEntity = multipartEntityBuilder.build();

                    message.setBody(httpEntity);

                    if (exchange.getIn().getHeader("Authorization") == null) {
                        message.setHeader("Authorization", "Bearer " + tokenService.getToken());
                    }

                    String url = faresUrl + "/import/netex";
                    url = url.replace("http4://", "http://").replaceAll("([^:])//+", "$1/");

                    exchange.setProperty("fares_url", url);
                    exchange.setProperty("PROVIDER_ID", exchange.getIn().getHeader(PROVIDER_ID));
                    exchange.setProperty("CORRELATION_ID", exchange.getIn().getHeader(CORRELATION_ID));
                    exchange.setProperty("CHOUETTE_REFERENTIAL", exchange.getIn().getHeader(CHOUETTE_REFERENTIAL));
                })
                .toD("${exchangeProperty.fares_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(Constants.JOB_STATUS_URL, getHttp4(faresUrl + e.getIn().getHeader("Location", String.class)));
                    e.getIn().setHeader(Constants.JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                    e.getIn().setHeader(PROVIDER_ID, e.getProperty("PROVIDER_ID"));
                    e.getIn().setHeader(CORRELATION_ID, e.getProperty("CORRELATION_ID"));
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, e.getProperty("CHOUETTE_REFERENTIAL"));
                })
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:faresProcessImportResult"))
                .setHeader(JOB_STATUS_JOB_TYPE, constant(TimetableAction.IMPORT_NETEX.name()))
                .removeHeader("loopCounter")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.IMPORT).state(State.STARTED).type(e.getIn().getHeader(FILE_TYPE, String.class)).build())
                .to(ROUTE_UPDATE_STATUS)
                .to("jms:queue:FaresPollStatusQueue")
                .routeId("fares-send-import-job");


        from("direct:faresProcessImportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setBody(constant(""))
                .choice()

                .when(simple("${exchangeProperty.STATUS} == 'FINISHED'"))
                     .process(e -> JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.OK).build())
                .when(simple("${exchangeProperty.STATUS} == 'FAILED'"))
                     .process(e -> {
                         JobEvent.providerJobBuilder(e).timetableAction(ImportRouteBuilder.getTimeTableAction(e)).state(State.FAILED).build();
                         createMail.createMail(e, null, ImportRouteBuilder.getTimeTableAction(e), false);
                     })
                .end()

                .to(ROUTE_UPDATE_STATUS)
                .routeId("fares-process-import-status");


    }

    private String getStringImportParameters(RawImportParameters rawImportParameters) {
        rawImportParameters.setProvider(getProviderRepository().getProvider(rawImportParameters.getProviderId()));
        return Parameters.createStringImportParameters(rawImportParameters);
    }
}


