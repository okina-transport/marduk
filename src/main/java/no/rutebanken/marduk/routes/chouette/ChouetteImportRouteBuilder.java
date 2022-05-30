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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.IdParameters;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.chouette.json.importer.ImportMode;
import no.rutebanken.marduk.routes.chouette.json.importer.RawImportParameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.Utils.getHttp4;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;

/**
 * Submits files to Chouette
 */
@Component
public class ChouetteImportRouteBuilder extends AbstractChouetteRouteBuilder {


    @Value("${chouette.url}")
    private String chouetteUrl;

    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();


//        RedeliveryPolicy chouettePolicy = new RedeliveryPolicy();
//        chouettePolicy.setMaximumRedeliveries(3);
//        chouettePolicy.setRedeliveryDelay(30000);
//        chouettePolicy.setRetriesExhaustedLogLevel(LoggingLevel.ERROR);
//        chouettePolicy.setRetryAttemptedLogLevel(LoggingLevel.WARN);
//        chouettePolicy.setLogExhausted(true);


//        onException(HttpOperationFailedException.class, NoRouteToHostException.class)
//                .setHeader(Constants.FILE_NAME, exchangeProperty(Constants.FILE_NAME))
//                .process(e -> Status.addStatus(e, TimetableAction.IMPORT, State.FAILED))
//                .to("direct:updateStatus")
//                .log(LoggingLevel.ERROR,correlation()+ "Failed while importing to Chouette")
//                .to("log:" + getClass().getName() + "?level=ERROR&showAll=true&multiline=true")
//                .handled(true);

        from("direct:chouetteCleanStopPlaces")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette stop place clean")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/clean/stop_areas"))
                .toD("${property.chouette_url}")
                .routeId("chouette-clean-stop-places");

        from("direct:chouetteCleanAllReferentials")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .removeHeaders("Camel*")
                .setHeader(Constants.PROVIDER_ID, simple("${body.id}"))
                .validate(header("filter").in("all", "level1", "level2"))
                .choice()
                .when(header("filter").isEqualTo("level1"))
                .filter(simple("${body.chouetteInfo.migrateDataToProvider} != null"))
                .setBody(constant(null))
                .to("direct:chouetteCleanReferential")
                .endChoice()
                .when(header("filter").isEqualTo("level2"))
                .filter(simple("${body.chouetteInfo.migrateDataToProvider} == null"))
                .setBody(constant(null))
                .to("direct:chouetteCleanReferential")
                .endChoice()
                .otherwise()
                .setBody(constant(null))
                .to("direct:chouetteCleanReferential")
                .end()
                .routeId("chouette-clean-referentials-for-all-providers");

        from("direct:chouetteCleanReferential")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette dataspace clean")
                .process(e -> {
                    Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
                })
                .removeHeaders("Camel*")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/clean"))
                .toD("${property.chouette_url}")
                .routeId("chouette-clean-dataspace");

        from("activemq:queue:ChouetteImportQueue?transacted=true").streamCaching()
                .transacted()
                .process(e -> log.info("debug purpose to remove"))
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette import")
                .removeHeader(Constants.CHOUETTE_JOB_ID)
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
                    .process(e -> {
                        Boolean analyze = e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null ? e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) : false;
                        TimetableAction action = analyze ? TimetableAction.FILE_ANALYZE : TimetableAction.IMPORT;
                        JobEvent.providerJobBuilder(e).timetableAction(action).state(State.FAILED).build();
                    })
                .otherwise()
                    .process(e -> {
                        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                        e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
                        e.getIn().setHeader(Constants.ENABLE_VALIDATION, provider.chouetteInfo.enableValidation);
                    })
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .to("direct:addImportParameters")
                .end()
                .routeId("chouette-import-dataspace");

        from("direct:addImportParameters")
                .process(e -> {
                    String fileName = e.getIn().getHeader(FILE_NAME, String.class);
                    String fileType = e.getIn().getHeader(FILE_TYPE, String.class);
                    Long providerId = e.getIn().getHeader(PROVIDER_ID, Long.class);
                    String splitCharacter = e.getIn().getHeader(SPLIT_CHARACTER, String.class);
                    String routeMergeStr = e.getIn().getHeader(ROUTE_MERGE, String.class);
                    boolean routeMerge = !StringUtils.isEmpty(routeMergeStr) && Boolean.parseBoolean(routeMergeStr);

                    String cleanMode = e.getIn().getHeader(CLEAN_MODE, String.class);


                    String keepBoardingAlightingPossibilityStr = e.getIn().getHeader(KEEP_BOARDING_ALIGHTING_POSSIBILITY, String.class);
                    boolean keepBoardingAlightingPossibility = !StringUtils.isEmpty(keepBoardingAlightingPossibilityStr) && Boolean.parseBoolean(keepBoardingAlightingPossibilityStr);

                    String keepStopGeolocalisationStr = e.getIn().getHeader(KEEP_STOP_GEOLOCALISATION, String.class);
                    boolean keepStopGeolocalisation = !StringUtils.isEmpty(keepStopGeolocalisationStr) && Boolean.parseBoolean(keepStopGeolocalisationStr);

                    String commercialPointIdPrefixToRemove = e.getIn().getHeader(COMMERCIAL_POINT_ID_PREFIX_TO_REMOVE, String.class);
                    String quayPrefixToRemove = e.getIn().getHeader(QUAY_ID_PREFIX_TO_REMOVE, String.class);
                    String stopAreaPrefixToRemove = e.getIn().getHeader(STOP_AREA_PREFIX_TO_REMOVE, String.class);
                    String areaCentroidPrefixToRemove = e.getIn().getHeader(AREA_CENTROID_PREFIX_TO_REMOVE, String.class);
                    String linePrefixToRemove = e.getIn().getHeader(LINE_PREFIX_TO_REMOVE, String.class);


                    String user = e.getIn().getHeader(USER, String.class);
                    String description = e.getIn().getHeader(DESCRIPTION, String.class);
                    IdParameters idParams = new IdParameters();
                    idParams.setQuayIdPrefixToRemove(quayPrefixToRemove);
                    idParams.setLinePrefixToRemove(linePrefixToRemove);

                    Boolean isAnalyzeJob = e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null ? e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) : false;

                    String ignoreCommercialPointsStr = e.getIn().getHeader(IGNORE_COMMERCIAL_POINTS, String.class);
                    boolean ignoreCommercialPoints = !StringUtils.isEmpty(ignoreCommercialPointsStr) && Boolean.parseBoolean(ignoreCommercialPointsStr);

                    if (StringUtils.isNotEmpty(commercialPointIdPrefixToRemove)) {
                        idParams.setCommercialPointIdPrefixToRemove(commercialPointIdPrefixToRemove);
                    } else {
                        idParams.setStopAreaPrefixToRemove(stopAreaPrefixToRemove);
                        idParams.setAreaCentroidPrefixToRemove(areaCentroidPrefixToRemove);
                    }

                    RawImportParameters rawImportParameters = new RawImportParameters();
                    String importModeStr = e.getIn().getHeader(IMPORT_MODE, String.class);

                    if (StringUtils.isNotEmpty(importModeStr)){
                        ImportMode importMode = ImportMode.valueOf(importModeStr);
                        rawImportParameters.setImportMode(importMode);
                    }


                    rawImportParameters.setFileName(fileName);
                    rawImportParameters.setFileType(fileType);
                    rawImportParameters.setProviderId(providerId);
                    rawImportParameters.setUser(user);
                    rawImportParameters.setDescription(description);
                    rawImportParameters.setRouteMerge(routeMerge);
                    rawImportParameters.setSplitCharacter(splitCharacter);
                    rawImportParameters.setIdParameters(idParams);
                    rawImportParameters.setCleanMode(cleanMode);
                    rawImportParameters.setIgnoreCommercialPoints(ignoreCommercialPoints);
                    rawImportParameters.setAnalyzeJob(isAnalyzeJob);
                    rawImportParameters.setKeepBoardingAlighting(keepBoardingAlightingPossibility);
                    rawImportParameters.setKeepStopGeolocalisation(keepStopGeolocalisation);


                    e.getIn().setHeader(JSON_PART, getStringImportParameters(rawImportParameters));
                }) //Using header to addToExchange json data
                .log(LoggingLevel.DEBUG, correlation() + "import parameters: " + header(JSON_PART))
                .to("direct:sendImportJobRequest")
                .routeId("chouette-import-addToExchange-parameters");

        from("direct:sendImportJobRequest")
                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .process(e -> toImportMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, "chouetteUrl: " + chouetteUrl)
                .choice()
                .when(simple("${header." + ANALYZE_ACTION + "}"))
                    .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/analyzeFile/${header." + FILE_TYPE + ".toLowerCase()}"))
                .otherwise()
                    .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/importer/${header." + FILE_TYPE + ".toLowerCase()}"))
                .end()
                .log(LoggingLevel.DEBUG, correlation() + "Calling Chouette with URL: ${exchangeProperty.chouette_url}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                // Attempt to retrigger delivery in case of errors
                .toD("${exchangeProperty.chouette_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_URL, getHttp4(e.getIn().getHeader("Location", String.class)));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processImportResult"))
                .choice()
                .when(simple("${header." + ANALYZE_ACTION + "}"))
                    .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(TimetableAction.FILE_ANALYZE.name()))
                .otherwise()
                    .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.IMPORT.name()))
                .end()
                .removeHeader("loopCounter")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-import-job");


        // Start common


        from("direct:processImportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setBody(constant(""))
                .choice()
                .when(PredicateBuilder.and(constant("true").isEqualTo(header(ANALYZE_ACTION)), simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'")))
                    .log(LoggingLevel.INFO, correlation() + "File analysis completed successfully")
                    .process(e -> {
                        JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.FILE_ANALYZE).state(State.OK).build();
                    })
                .when(PredicateBuilder.and(constant("false").isEqualTo(header(Constants.ENABLE_VALIDATION)), simple("${header.action_report_result} == 'OK'")))
                    .to("direct:checkScheduledJobsBeforeTriggeringNextAction")
                    .process(e -> {
                                JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.OK).build();
                            })
                .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'"))
                    .to("direct:checkScheduledJobsBeforeTriggeringNextAction")
                    .process(e -> {
                        JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.OK).build();
                    })
                .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'NOK'"))
                    .log(LoggingLevel.INFO, correlation() + "Import ok but validation failed")
                    .process(e -> {
                        JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                    })
                .when(simple("${header.action_report_result} == 'NOK'"))
                    .choice()
                    .when(simple("${header.action_report_result} == 'NOK'"))
                      .log(LoggingLevel.WARN, correlation() + "Import not ok")
                        .process(e -> {
                            JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                        })
                    .endChoice()
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + "Something went wrong on import")
                    .process(e -> {
                        JobEvent.providerJobBuilder(e).timetableAction(getTimeTableAction(e)).state(State.FAILED).build();
                    })
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-import-status");

        // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
        from("direct:checkScheduledJobsBeforeTriggeringNextAction")
                .setProperty("job_status_url", simple("{{chouette.url}}/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?timetableAction=importer&status=SCHEDULED&status=STARTED"))
                .toD("${exchangeProperty.job_status_url}")
                .choice()
                .when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
                .log(LoggingLevel.INFO, correlation() + "Import and validation ok, skipping next step as there are more import jobs active")
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Import and validation ok, triggering next step.")
                .setBody(constant(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(constant("true").isEqualTo(header(Constants.ENABLE_VALIDATION)))
                .log(LoggingLevel.INFO, correlation() + "Import ok, triggering validation")
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name()))
                .to("activemq:queue:ChouetteValidationQueue")
                .when(method(getClass(), "shouldTransferData").isEqualTo(true))
                .log(LoggingLevel.INFO, correlation() + "Import ok, transfering data to next dataspace")
                .to("activemq:queue:ChouetteTransferExportQueue")
                .when(method(getClass(), "isAutoTransferData").isEqualTo(true))
                .log(LoggingLevel.INFO, correlation() + "Import ok, triggering export")
                .to("activemq:queue:ChouetteExportNetexQueue")
                .end()
                .end()
                .routeId("chouette-process-job-list-after-import");

    }

    private TimetableAction getTimeTableAction(Exchange e){
        Boolean analyze = e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null ? e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) : false;
        return analyze ? TimetableAction.FILE_ANALYZE : TimetableAction.IMPORT;
    }

    private String getStringImportParameters(RawImportParameters rawImportParameters) {
        rawImportParameters.setProvider(getProviderRepository().getProvider(rawImportParameters.getProviderId()));
        return Parameters.createStringImportParameters(rawImportParameters);
    }

}


