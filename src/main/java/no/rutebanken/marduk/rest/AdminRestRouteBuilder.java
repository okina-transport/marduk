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

package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.BlobStoreFiles.File;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.blobstore.BlobStoreRoute;
import no.rutebanken.marduk.routes.chouette.ExportJsonMapper;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.AuthorizationClaim;
import no.rutebanken.marduk.security.AuthorizationService;
import no.rutebanken.marduk.services.BlobStoreService;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.commons.io.FileUtils;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static no.rutebanken.marduk.Constants.*;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {


    private static final String JSON = "application/json";
    private static final String X_OCTET_STREAM = "application/x-octet-stream";
    private static final String PLAIN = "text/plain";
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmssZ");


    @Value("${server.admin.port}")
    public String port;

    @Value("${server.admin.host}")
    public String host;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ExportJsonMapper exportJsonMapper;

    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    FileSystemService fileSystemService;

    @Value("${superspace.name}")
    private String superspaceName;

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String netexWorkingDirectory;

    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization, x-okina-referential, RutebankenUser, RutebankenDescription, EXPORT_LINES_IDS, EXPORT_START_DATE, EXPORT_END_DATE, ImportType, routeMerge, splitCharacter, commercialPointIdPrefixToRemove, quayIdPrefixToRemove, areaCentroidPrefixToRemove, linePrefixToRemove, stopAreaPrefixToRemove,ignoreCommercialPoints,analysisJobId, cleanRepository,keepBoardingAlightingPossibility,keepStopGeolocalisation");

        RestPropertyDefinition corsAllowedOrigin = new RestPropertyDefinition();
        corsAllowedOrigin.setKey("Access-Control-Allow-Origin");
        corsAllowedOrigin.setValue("*");

        restConfiguration().setCorsHeaders(Arrays.asList(corsAllowedHeaders, corsAllowedOrigin));


        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        restConfiguration()
                .component("jetty")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("filtersRef", "keycloakPreAuthActionsFilter,keycloakAuthenticationProcessingFilter")
                .endpointProperty("sessionSupport", "true")
                .endpointProperty("matchOnUriPrefix", "true")
                .endpointProperty("enablemulti-partFilter", "true")
                .enableCORS(true)
                .dataFormatProperty("prettyPrint", "true")
                .host(host)
                .port(port)
                .apiContextPath("/swagger.json")
                .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")
                .contextPath("/services");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get().route().routeId("admin-route-authorize-get").throwException(new NotFoundException()).endRest()
                .post().route().routeId("admin-route-authorize-post").throwException(new NotFoundException()).endRest()
                .put().route().routeId("admin-route-authorize-put").throwException(new NotFoundException()).endRest()
                .delete().route().routeId("admin-route-authorize-delete").throwException(new NotFoundException()).endRest();


        String commonApiDocEndpoint = "rest:get:/services/swagger.json?bridgeEndpoint=true";

        rest("/timetable_admin")
                .post("/idempotentfilter/clean")
                .description("Clean unique filename and digest Idempotent Stores")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-application-clean-unique-filename-and-digest-idempotent-repos")
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .to("direct:cleanIdempotentFileStore")
                .setBody(constant(null))
                .endRest()

                .post("/validate/level1")
                .description("Triggers the validate->transfer process for all level1 providers in Chouette")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation level1 for all providers")
                .removeHeaders("CamelHttp*")
                .inOnly("direct:chouetteValidateLevel1ForAllProviders")
                .setBody(constant(null))
                .routeId("admin-chouette-validate-level1-all-providers")
                .endRest()

                .post("/validate/level2")
                .description("Triggers the validate->export process for all level2 providers in Chouette")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation level2 for all providers")
                .removeHeaders("CamelHttp*")
                .inOnly("direct:chouetteValidateLevel2ForAllProviders")
                .setBody(constant(null))
                .routeId("admin-chouette-validate-level2-all-providers")
                .endRest()

                .get("/jobs")
                .description("List Chouette jobs for all providers. Filters defaults to status=SCHEDULED,STARTED")
                .param()
                .required(Boolean.FALSE)
                .name("status")
                .type(RestParamType.query)
                .description("Chouette job statuses")
                .allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name("action")
                .type(RestParamType.query)
                .description("Chouette job types")
                .allowableValues("importer", "exporter", "validator")
                .endParam()
                .outType(ProviderAndJobs[].class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.DEBUG, correlation() + "Get chouette active jobs all providers")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader("status", e.getIn().getHeader("status") != null ? e.getIn().getHeader("status") : Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:chouetteGetJobsAll")
                .routeId("admin-chouette-list-jobs-all")
                .endRest()

                .delete("/jobs")
                .description("Cancel all Chouette jobs for all providers")
                .responseMessage().code(200).message("All jobs canceled").endResponseMessage()
                .responseMessage().code(500).message("Could not cancel all jobs").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs for all providers")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCancelAllJobsForAllProviders")
                .routeId("admin-chouette-cancel-all-jobs-all")
                .setBody(constant(null))
                .endRest()

                .delete("/completed_jobs")
                .description("Remove completed Chouette jobs for all providers. ")
                .param()
                .required(Boolean.FALSE)
                .name("keepJobs")
                .type(RestParamType.query)
                .dataType("integer")
                .description("No of jobs to keep, regardless of age")
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name("keepDays")
                .type(RestParamType.query)
                .dataType("integer")
                .description("No of days to keep jobs for")
                .endParam()
                .responseMessage().code(200).message("Completed jobs removed").endResponseMessage()
                .responseMessage().code(500).message("Could not remove complete jobs").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Removing old chouette jobs for all providers")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteRemoveOldJobs")
                .routeId("admin-chouette-remove-old-jobs")
                .setBody(constant(null))
                .endRest()


                .post("/clean/{filter}")
                .description("Triggers the clean ALL dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations) or stop places")
                .param()
                .required(Boolean.TRUE)
                .name("filter")
                .type(RestParamType.path)
                .description("Optional filter to clean only level 1, level 2 or all spaces (no parameter value)")
                .allowableValues("all", "level1", "level2")

                .endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette clean all dataspaces")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCleanAllReferentials")
                .setBody(constant(null))
                .routeId("admin-chouette-clean-all")
                .endRest()

                .post("/stop_places/clean")
                .description("Triggers the cleaning of ALL stop places in Chouette")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette clean all stop places")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCleanStopPlaces")
                .setBody(constant(null))
                .routeId("admin-chouette-clean-stop-places")
                .endRest()

                .get("/line_statistics/{filter}")
                .description("List stats about data in chouette for multiple providers")
                .param().name("providerIds")
                .type(RestParamType.query).dataType("integer")
                .required(Boolean.FALSE)
                .description("Comma separated list of id for providers to fetch line stats for")
                .endParam()
                .param()
                .name("filter")
                .required(Boolean.TRUE)
                .type(RestParamType.path)
                .description("Filter to fetch statistics for only level 1, level 2 or all spaces")
                .allowableValues("all", "level1", "level2")
                .endParam()
                .bindingMode(RestBindingMode.off)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, correlation() + "get stats for multiple providers")
                .removeHeaders("CamelHttp*")
                .choice()
                .when(simple("${header.providerIds}"))
                .process(e -> e.getIn().setHeader(PROVIDER_IDS, e.getIn().getHeader("providerIds", "", String.class).split(",")))
                .end()
                .to("direct:chouetteGetStats")
                .routeId("admin-chouette-stats-multiple-providers")
                .endRest()

                .post("/line_statistics/refresh")
                .description("Recalculate stats about data in chouette for all providers")
                .bindingMode(RestBindingMode.off)
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, correlation() + "refresh stats cache")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteRefreshStatsCache")
                .routeId("admin-chouette-stats-refresh-cache")
                .endRest()

                .get("/export/files")
                .description("List files containing exported time table data and graphs")
                .outType(BlobStoreFiles.class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, correlation() + "get time table and graph files")
                .removeHeaders("CamelHttp*")
                .to("direct:listTimetableExportAndGraphBlobs")
                .routeId("admin-chouette-timetable-files-get")
                .endRest()

                .get("/export/files/{providerId}")
                .description("List files containing exported time table data and graphs for specified providerId")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .outType(BlobStoreFiles.class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "get time table and graph files")
                .removeHeaders("CamelHttp*")
                .to("direct:listTimetableExportAndGraphBlobsByProvider")
                .routeId("admin-chouette-timetable-files-get-provider")
                .endRest()


                .post("/export/gtfs/extended")
                .description("Prepare and upload GTFS extened export")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered GTFS extended export")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:GtfsExportMergedQueue")
                .routeId("admin-timetable-gtfs-extended-export")
                .endRest()


                .post("/export/gtfs/basic")
                .description("Prepare and upload GTFS basic export")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered GTFS basic export")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:GtfsBasicExportMergedQueue")
                .routeId("admin-timetable-gtfs-basic-export")
                .endRest()

                .post("/export/gtfs/google")
                .description("Prepare and upload GTFS export to Google")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered GTFS export to Google")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:GoogleExportQueue")
                .routeId("admin-timetable-google-export")
                .endRest()

                .post("/export/gtfs/google-qa")
                .description("Prepare and upload GTFS QA export to Google")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered GTFS QA export to Google")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:GoogleQaExportQueue")
                .routeId("admin-timetable-google-qa-export")
                .endRest()

                .post("/export/google/publish")
                .description("Upload GTFS export to Google")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered publish of GTFS to Google")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:GooglePublishQueue")
                .routeId("admin-timetable-google-publish")
                .endRest()

                .post("/export/google-qa/publish/")
                .description("Upload GTFS QA export to Google")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered publish of GTFS QA export to Google")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:GooglePublishQaQueue")
                .routeId("admin-timetable-google-qa-publish")
                .endRest()


                .post("/export/netex/merged")
                .description("Prepare and upload a merged Netex file for Norway")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, "Triggered Netex export of merged file for Norway")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:NetexExportMergedQueue")
                .routeId("admin-timetable-netex-merged-export")
                .endRest()

                .get("/swagger.json")
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest()

                .post("routing_graph/build_base")
                .description("Triggers building of the OTP base graph using map data (osm + height)")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "Triggered build of OTP base graph with map data")
                .removeHeaders("CamelHttp*")
                .setBody(simple(""))
                .setHeader(Constants.OTP_BASE_GRAPH_BUILD, constant(true))
                .inOnly("activemq:queue:OtpGraphBuildQueue")
                .routeId("admin-build-base-graph")
                .endRest()

                .post("routing_graph/build")
                .description("Triggers building of the OTP graph using existing NeTEx and and a pre-prepared base graph with map data")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "OTP build graph from NeTEx")
                .removeHeaders("CamelHttp*")
                .setBody(simple(""))
                .inOnly("activemq:queue:OtpGraphBuildQueue")
                .routeId("admin-build-graph-netex")
                .endRest()

                .post("routing_graph/qa")
                .description("Triggers running the OTP TravelSearch QA (tests)")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "Trigger OTP TravelSearch QA")
                .removeHeaders("CamelHttp*")
                .setBody(simple(""))
                .to("direct:runOtpTravelSearchQA")
                .routeId("admin-otp-travelsearch-qa")
                .endRest();


        rest("/timetable_admin/{providerId}")
                .post("/import")
                .description("Triggers the import->validate->export process in Chouette for each blob store file handle. Use /files call to obtain available files. Files are imported in the same order as they are provided")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .type(BlobStoreFiles.class)
                .outType(String.class)
                .consumes(JSON)
                .produces(PLAIN)
                .responseMessage().code(200).message("Job accepted").endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .removeHeaders("CamelHttp*")
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(IMPORT, constant(true))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .split(method(ImportFilesSplitter.class, "splitFiles"))

                .process(e -> e.getIn().setHeader(FILE_HANDLE, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).mobiitiId
                        + "/imports/" + e.getIn().getBody(String.class)))
                .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                .log(LoggingLevel.INFO, correlation() + "Chouette start import fileHandle=${body}")

                .process(e -> {
                    String fileNameForStatusLogging = "reimport-" + e.getIn().getBody(String.class);
                    e.getIn().setHeader(FILE_NAME, fileNameForStatusLogging);
                })
                .setBody(constant(null))

                .inOnly("activemq:queue:ProcessFileQueue")
                .routeId("admin-chouette-import")
                .endRest()

                .get("/files")
                .description("List files available for reimport into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the baba service").dataType("integer").endParam()
                .outType(BlobStoreFiles.class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "blob store get files")
                .removeHeaders("CamelHttp*")
                .to("direct:listBlobsFlat")
                .routeId("admin-chouette-import-list")
                .endRest()

                .post("/analyzeFile")
                .description("Upload file for pre-import analyze into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .streamCaching()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .process(e -> log.info("Authorized request passed"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> log.info("validation passed"))
                .process(e -> e.getIn().setHeader(ANALYZE_ACTION, true))
                .log(LoggingLevel.INFO, correlation() + "upload files and start import pipeline")
                .removeHeaders("CamelHttp*")
                .to("direct:uploadFilesAndStartImport")
                .routeId("admin-chouette-upload-file-to-analysis")
                .endRest()


                .post("/files")
                .description("Upload file for import into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .streamCaching()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .process(e -> log.info("Authorized request passed"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> log.info("validation passed"))
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class)));
                    String analysisJobId = e.getIn().getHeader(ANALYSIS_JOB_ID, String.class);
                    java.io.File file = fileSystemService.getAnalysisFile(e);
                    FileItemFactory fac = new DiskFileItemFactory();
                    FileItem fileItem = fac.createItem("file", "application/zip",false, file.getName());
                    Streams.copy(new FileInputStream(file), fileItem.getOutputStream(), true);
                    e.getIn().setBody(fileItem);
                })
                .log(LoggingLevel.INFO, correlation() + "upload files and start import pipeline")
                .removeHeaders("CamelHttp*")
                .to("direct:importLaunch")
                .routeId("admin-chouette-upload-file")
                .endRest()

                .get("/files/{fileName}")
                .description("Download file for reimport into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .param().name("fileName").type(RestParamType.path).description("Name of file to fetch").dataType("string").endParam()
                .consumes(PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid fileName").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> e.getIn().setHeader("fileName", URLDecoder.decode(e.getIn().getHeader("fileName", String.class), "utf-8")))
                .process(e -> e.getIn().setHeader(FILE_HANDLE, BLOBSTORE_PATH_INBOUND
                        + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
                        + "/" + e.getIn().getHeader("fileName", String.class)))
                .log(LoggingLevel.INFO, correlation() + "blob store download file by name")
                .removeHeaders("CamelHttp*")
                .to("direct:getBlob")
                .choice().when(simple("${body} == null")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).endChoice()
                .routeId("admin-chouette-file-download")
                .endRest()

                .get("/files/stop-places")
                .description("Download stop places export file (NeTEx stop places)")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader("Access-Control-Expose-Headers", simple(FILE_NAME))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> {
                    String ref = e.getIn().getHeader(OKINA_REFERENTIAL, String.class);
                    if (!ref.contains(superspaceName + "_")) {
                        e.getIn().setHeader(OKINA_REFERENTIAL, superspaceName + "_" + ref);
                    } else {
                        e.getIn().setHeader(OKINA_REFERENTIAL, ref);
                    }
                })
                .removeHeaders("CamelHttp*")
                .to("direct:getStopPlacesFile")
                .choice().when(simple("${body} == null")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).endChoice()
                .routeId("admin-stop-places-file-download")
                .endRest()

                .get("/files/offer/{jobId}")
                .description("Download offer export file (GTFS, NeTEx or Concerto")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .param().name("jobId").type(RestParamType.path).description("Job id").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId or jobId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(CHOUETTE_JOB_ID, header("jobId"))
                .setHeader("Access-Control-Expose-Headers", simple(FILE_NAME))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> {
                    String ref = e.getIn().getHeader(OKINA_REFERENTIAL, String.class);
                    if (!ref.contains(superspaceName + "_")) {
                        e.getIn().setHeader(OKINA_REFERENTIAL, superspaceName + "_" + ref);
                    } else {
                        e.getIn().setHeader(OKINA_REFERENTIAL, ref);
                    }
                })
                .removeHeaders("CamelHttp*")
                .to("direct:getOfferFile")
                .choice().when(simple("${body} == null")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).endChoice()
                .routeId("admin-offer-file-download")
                .endRest()

                .get("/line_statistics")
                .description("List stats about data in chouette for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .bindingMode(RestBindingMode.off)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "get stats")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteGetStatsSingleProvider")
                .routeId("admin-chouette-stats")
                .endRest()

                .get("/jobs")
                .description("List Chouette jobs for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .param()
                .required(Boolean.FALSE)
                .name("status")
                .type(RestParamType.query)
                .description("Chouette job statuses")
                .allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name("action")
                .type(RestParamType.query)
                .description("Chouette job types")
                .allowableValues("importer", "exporter", "validator")
                .endParam()
                .outType(JobResponse[].class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Get chouette jobs status=${header.status} action=${header.action}")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteGetJobsForProvider")
                .routeId("admin-chouette-list-jobs")
                .endRest()

                .delete("/jobs")
                .description("Cancel all Chouette jobs for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Job deleted").endResponseMessage()
                .responseMessage().code(500).message("Invalid jobId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCancelAllJobsForProvider")
                .routeId("admin-chouette-cancel-all-jobs")
                .endRest()

                .delete("/jobs/{jobId}")
                .description("Cancel a Chouette job for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .param().name("jobId").type(RestParamType.path).description("Job id as returned in any of the /jobs GET calls").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Job deleted").endResponseMessage()
                .responseMessage().code(500).message("Invalid jobId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(CHOUETTE_JOB_ID, header("jobId"))
                .log(LoggingLevel.INFO, correlation() + "Cancel chouette job")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCancelJob")
                .routeId("admin-chouette-cancel-job")
                .endRest()

                .post("/export")
                .description("Triggers the export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(NO_GTFS_EXPORT, constant(false))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(false))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:ChouetteExportNetexQueue")
                .routeId("admin-chouette-export")
                .endRest()

                .post("/export/netex")
                .description("Triggers the Netex export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(NO_GTFS_EXPORT, constant(true))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(false))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Netex")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader(USER, getUserNameFromHeaders(e)))
                .inOnly("activemq:queue:ChouetteExportNetexQueue")
                .routeId("admin-chouette-export-netex")
                .endRest()

                .post("/export/netex_global")
                .description("Triggers the Netex export global process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(NO_GTFS_EXPORT, constant(true))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(true))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Netex global")
                .removeHeaders("CamelHttp*")
                .inOnly("direct:launchGlobalNetexExport")
                .routeId("admin-chouette-export-netex-global")
                .endRest()

                .post("/export/gtfs")
                .description("Triggers the Gtfs export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(GTFS_EXPORT_GLOBAL, constant(false))
                .setHeader(KEEP_ORIGINAL_ID, constant(false))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export GTFS")
                .removeHeaders("CamelHttp*")
                .process(this::getFromHeadersForGTFS)
                .inOnly("activemq:queue:ChouetteExportGtfsQueue")
                .routeId("admin-chouette-export-gtfs")
                .endRest()

                .post("/export/gtfs_global")
                .description("Triggers the Gtfs export global process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .setHeader(GTFS_EXPORT_GLOBAL, constant(true))
                .setHeader(KEEP_ORIGINAL_ID, constant(true))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Gtfs global")
                .removeHeaders("CamelHttp*")
                .process(this::getFromHeadersForGTFS)
                .inOnly("direct:chouetteGtfsExportForAllProviders")
                .routeId("admin-chouette-export-gtfs-global")
                .endRest()

                .post("/export/all")
                .description("Triggers all exports process in Chouette.")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command for all exports accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start all export process")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader(USER, getUserNameFromHeaders(e)))
                .inOnly("activemq:queue:predefinedExports")
                .routeId("admin-chouette-export-all")
                .endRest()


                .post("/export/concerto")
                .description("Triggers the Concerto export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Concerto")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader(USER, getUserNameFromHeaders(e)))
                .inOnly("activemq:queue:ChouetteExportConcertoQueue")
                .routeId("admin-chouette-export-concerto")
                .endRest()

                .post("/export/stops")
                .description("Triggers the stops export process in Tiamat. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Tiamat start export Stops")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader(USER, getUserNameFromHeaders(e)))
                .inOnly("activemq:queue:TiamatStopPlacesExport")
                .routeId("admin-tiamat-export-stops")
                .endRest()

                .post("/validate")
                .description("Triggers the validate->export process in Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(JSON)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader(USER, getUserNameFromHeaders(e)))
                .choice().when(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.migrateDataToProvider == null)
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_2.name()))
                .otherwise()
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name()))
                .end()
                .inOnly("activemq:queue:ChouetteValidationQueue")
                .routeId("admin-chouette-validate")
                .endRest()


                .post("/public-export")
                .description("Triggers the public-export")
                .param().name("public").type(RestParamType.query).description("Defines if export file should be set as public").dataType("boolean").endParam()
                .consumes(JSON)
                .produces(JSON)
                .responseMessage().code(200).message("Public-export command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .process(e -> {
                    log.info("Public-export: exports parsing");
                    Optional<Provider> provider = getProviderRepository().getNonMobiitiProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    boolean isPublic = Boolean.valueOf(e.getIn().getHeader("public").toString());

                    String json = exportJsonMapper.toJson(e.getIn().getBody());
                    List<ExportTemplate> exports = exportJsonMapper.fromJsonArray(json);
                    provider.ifPresent(p -> {
                        exports.stream().forEach(export -> {
                            String exportFilePath = BlobStoreRoute.exportFilePath(export, p);
                            blobStoreService.setPublicAccess(exportFilePath, isPublic);
                        });
                    });
                    log.info("Public-export: exports parsing end ...");
                })
                .routeId("admin-public-export")
                .endRest()

                .post("/delete-exports")
                .description("Delete all exports linked to provider")
                .responseMessage().code(200).message("Delete exports command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .process(e -> {
                    log.info("Delete exports starting");
                    Provider provider = getProviderRepository().getNonMobiitiProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))
                            .orElseThrow(() -> new RuntimeException("No valid base provider found. Provider id : " + e.getIn().getHeader(PROVIDER_ID)));
                    String baseProviderFolder = BlobStoreRoute.exportSiteId(provider);
                    blobStoreService.deleteAllBlobsInFolder(baseProviderFolder, e);
                    blobStoreService.deleteBlob(baseProviderFolder, e);
                    log.info("Delete exports done");
                })
                .routeId("admin-delete-exports")
                .endRest()

                .post("/clean")
                .description("Triggers the clean dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations)")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette clean dataspace")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCleanReferential")
                .routeId("admin-chouette-clean")
                .endRest()

                .post("/transfer")
                .description("Triggers transfer of data from one dataspace to the next")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("integer").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .log(LoggingLevel.INFO, correlation() + "Chouette transfer dataspace")
                .removeHeaders("CamelHttp*")
                .setHeader(PROVIDER_ID, header("providerId"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .inOnly("activemq:queue:ChouetteTransferExportQueue")
                .routeId("admin-chouette-transfer")
                .endRest();


        rest("/map_admin")
                .post("/download")
                .description("Triggers downloading of the latest OSM data")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "OSM update map data")
                .removeHeaders("CamelHttp*")
                .to("direct:considerToFetchOsmMapOverNorway")
                .routeId("admin-fetch-osm")
                .endRest()

                .post("/mapbox_update")
                .description("Triggers update of mapbox tileset from tiamat data")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "Mapbox update with data from tiamat")
                .removeHeaders("CamelHttp*")
                .to("direct:runMapboxUpdate")
                .routeId("admin-update-mapbox")
                .endRest()

                .get("/swagger.json")
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();

        from("direct:authorizeRequest")
                .doTry()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN),
                        new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT, e.getIn().getHeader(PROVIDER_ID, Long.class))))
                .routeId("admin-authorize-request");


        from("direct:launchGlobalNetexExport")
                .setHeader(Exchange.FILE_PARENT, simple(netexWorkingDirectory + "/netex/allFiles"))
                .inOnly("direct:cleanUpLocalDirectory")
                .process(e -> e.getIn().setHeader(USER, getUserNameFromHeaders(e)))
                .inOnly("direct:resetExportLists")
                .inOnly("direct:chouetteNetexExportForAllProviders")
                .inOnly("direct:exportMergedNetex")
                .routeId("launch-global-netex-export");

    }

    public static class ImportFilesSplitter {
        public List<String> splitFiles(@Body BlobStoreFiles files) {
            return files.getFiles().stream().map(File::getName).collect(Collectors.toList());
        }
    }

    private String getUserNameFromHeaders(Exchange e) {
        Map body = (Map) e.getIn().getBody(Map.class);
        Map headers;
        headers = body == null ?  e.getIn().getHeaders() : (Map) body.get("headers");

        if (headers != null) {
            return (String) headers.get(USER);
        }
        return null;
    }

    private void getFromHeadersForGTFS(Exchange e) {
        Map headers = (Map) e.getIn().getBody(Map.class).get("headers");
        if (headers != null) {
            if (headers.get(USER) != null) {
                e.getIn().setHeader(USER, headers.get(USER));
            }
            if (headers.get(EXPORT_LINES_IDS) != null) {
                e.getIn().setHeader(EXPORT_LINES_IDS, headers.get(EXPORT_LINES_IDS));
            }
            if (headers.get(EXPORT_START_DATE) != null) {
                e.getIn().setHeader(EXPORT_START_DATE, headers.get(EXPORT_START_DATE));
            }
            if (headers.get(EXPORT_END_DATE) != null) {
                e.getIn().setHeader(EXPORT_END_DATE, headers.get(EXPORT_END_DATE));
            }
            if (headers.get(EXPORT_NAME) != null) {
                e.getIn().setHeader(EXPORT_NAME, headers.get(EXPORT_NAME));
            }
        }
    }

}


