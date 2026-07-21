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

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import no.rutebanken.marduk.domain.*;
import no.rutebanken.marduk.domain.BlobStoreFiles.File;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.blobstore.BlobStoreRoute;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.AuthorizationClaim;
import no.rutebanken.marduk.security.AuthorizationService;
import no.rutebanken.marduk.services.*;
import no.rutebanken.marduk.services.processors.FileValidationProcessor;
import no.rutebanken.marduk.services.processors.MultiPartProcessor;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.constants.EndpointDeclarationConstants.*;
import static no.rutebanken.marduk.utils.constants.MessageConstants.*;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.*;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.*;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Description.PROVIDER_DESCRIPTION;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Headers.ALL_CAMEL_HTTP;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Headers.CRON_EXPRESSION;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.JOB_ID;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.ParamTypes.INTEGER;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.QueryParams.*;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.SimpleExpression.BODY_IS_NULL;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    private static final String X_OCTET_STREAM = "application/x-octet-stream";
    private static final String CAMEL_HEADERS = "${headers}";

    @Value("${server.admin.host}")
    public String host;

    @Value("${superspace.name}")
    private String superspaceName;

    @Value("${simulation.name}")
    private String simulationName;

    @Value("${netex.merged.tmp.working.directory:/tmp/mergedNetex/allFiles}")
    private String mergedNetexTmpDirectory;

    private final AuthorizationService authorizationService;

    private final BlobStoreService blobStoreService;

    private final FileSystemService fileSystemService;

    private final MultiPartProcessor multiPartProcessor;

    private final FileValidationProcessor fileValidationProcessor;

    private final ChouetteValidationScheduleService chouetteValidationScheduleService;

    private final FaresScheduleService faresScheduleService;

    private final UserActionsLoggingService userActionsLoggingService;

    public AdminRestRouteBuilder(AuthorizationService authorizationService,
                                 BlobStoreService blobStoreService,
                                 FileSystemService fileSystemService,
                                 MultiPartProcessor multiPartProcessor,
                                 FileValidationProcessor fileValidationProcessor,
                                 ChouetteValidationScheduleService chouetteValidationScheduleService,
                                 FaresScheduleService faresScheduleService, UserActionsLoggingService userActionsLoggingService) {
        this.authorizationService = authorizationService;
        this.blobStoreService = blobStoreService;
        this.fileSystemService = fileSystemService;
        this.multiPartProcessor = multiPartProcessor;
        this.fileValidationProcessor = fileValidationProcessor;
        this.chouetteValidationScheduleService = chouetteValidationScheduleService;
        this.faresScheduleService = faresScheduleService;
        this.userActionsLoggingService = userActionsLoggingService;
    }


    @Override
    public void configure() throws Exception {
        super.configure();

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, " +
                "Access-Control-Request-Headers, Authorization, x-okina-referential, RutebankenUser, RutebankenDescription, EXPORT_LINES_IDS, EXPORT_START_DATE, EXPORT_END_DATE, ImportType, routeMerge, splitCharacter, commercialPointIdPrefixToRemove, quayIdPrefixToRemove, areaCentroidPrefixToRemove, linePrefixToRemove, stopAreaPrefixToRemove, ignoreCommercialPoints, analysisJobId, cleanMode, keepBoardingAlightingPossibility, keepStopGeolocalisation, keepStopNames, removeParentStations, importShapesFile, updateStopAccessibility, railUICprocessing, generateMapMatching, routesReorganization, distanceGeolocation, routeSortOrder, netexImportLayouts, netexImportColors, useTargetNetwork, targetNetwork, renameRoutesAfterMerge, importFareFiles, recomputeStopPlacesLocation, importTargetRoutes, exportGeneratedMissingQuays, overwriteLineInformation, exportExternalIds, CronExpression");

        RestPropertyDefinition corsAllowedOrigin = new RestPropertyDefinition();
        corsAllowedOrigin.setKey("Access-Control-Allow-Origin");
        corsAllowedOrigin.setValue("*");

        restConfiguration().setCorsHeaders(Arrays.asList(corsAllowedHeaders, corsAllowedOrigin));


        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_PLAIN))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_PLAIN))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_PLAIN))
                .transform(exceptionMessage());

        // Remove "Authorization" header from all REST responses which make MARDUK crash sometimes
        // (java.io.IOException: org.eclipse.jetty.http.BadMessageException: 500: Response header too large)
        interceptFrom("rest:*")
                .log(LoggingLevel.INFO, "Remove Authorization header")
                .process(e -> e.getMessage().removeHeader("Authorization"));

        restConfiguration()
                .component("servlet")
                .contextPath(CAMEL_ENTRYPOINT)
                .bindingMode(RestBindingMode.json)
                .endpointProperty("matchOnUriPrefix", "true")
                .endpointProperty("attachmentmultipartbinding", "true")
                .enableCORS(true)
                .host(host)
                .apiContextPath(SWAGGER_ENDPOINT)
                .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0");


        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get().to(ROUTE_ADMIN_ROUTE_AUTHORIZE_GET)
                .post().to(ROUTE_ADMIN_ROUTE_AUTHORIZE_POST)
                .put().to(ROUTE_ADMIN_ROUTE_AUTHORIZE_PUT)
                .delete().to(ROUTE_ADMIN_ROUTE_AUTHORIZE_DELETE);

        from(ROUTE_ADMIN_ROUTE_AUTHORIZE_GET)
                .routeId(ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_GET)
                .throwException(new NotFoundException());

        from(ROUTE_ADMIN_ROUTE_AUTHORIZE_POST)
                .routeId(ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_POST)
                .throwException(new NotFoundException());

        from(ROUTE_ADMIN_ROUTE_AUTHORIZE_PUT)
                .routeId(ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_PUT)
                .throwException(new NotFoundException());

        from(ROUTE_ADMIN_ROUTE_AUTHORIZE_DELETE)
                .routeId(ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_DELETE)
                .throwException(new NotFoundException());

        String commonApiDocEndpoint = "rest:get:/services/swagger.json?bridgeEndpoint=true";

        rest("/timetable_admin")
                .post("/idempotentfilter/clean")
                .description("Clean unique filename and digest Idempotent Stores")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_APPLICATION_CLEAN_UNIQUE_FILENAME_AND_DIGEST_IDEMPOTENT_REPOS)

                .post("/validate/level1")
                .description("Triggers the validate->transfer process for all level1 providers in Chouette")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_PROCESS_VALIDATE_LEVEL_1)

                .post("/validate/level2")
                .description("Triggers the validate->export process for all level2 providers in Chouette")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_PROCESS_VALIDATE_LEVEL_2)

                .get(JOBS_ENDPOINT)
                .description("List Chouette jobs for all providers. Filters defaults to status=SCHEDULED,STARTED")
                .param()
                .required(Boolean.FALSE)
                .name(STATUS)
                .type(RestParamType.query)
                .description("Chouette job statuses")
                .allowableValues(Arrays.stream(Status.values()).map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name(ACTION)
                .type(RestParamType.query)
                .description("Chouette job types")
                .allowableValues(IMPORTER, EXPORTER, VALIDATOR)
                .endParam()
                .outType(ProviderAndJobs[].class)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_LIST_ALL_CHOUETTE_JOBS)

                .delete(JOBS_ENDPOINT)
                .description("Cancel all Chouette jobs for all providers")
                .responseMessage().code(200).message("All jobs canceled").endResponseMessage()
                .responseMessage().code(500).message("Could not cancel all jobs").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_CANCEL_ALL_JOBS_ALL)

                .delete("/tiamat-jobs")
                .description("Cancel all Tiamat jobs for all providers")
                .responseMessage().code(200).message("All jobs canceled").endResponseMessage()
                .responseMessage().code(500).message("Could not cancel all jobs").endResponseMessage()
                .to(ROUTE_ADMIN_TIAMAT_CANCEL_ALL_JOBS)

                .delete("/completed_jobs")
                .description("Remove completed Chouette jobs for all providers. ")
                .param()
                .required(Boolean.FALSE)
                .name("keepJobs")
                .type(RestParamType.query)
                .dataType(INTEGER)
                .description("No of jobs to keep, regardless of age")
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name("keepDays")
                .type(RestParamType.query)
                .dataType(INTEGER)
                .description("No of days to keep jobs for")
                .endParam()
                .responseMessage().code(200).message("Completed jobs removed").endResponseMessage()
                .responseMessage().code(500).message("Could not remove complete jobs").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_REMOVE_OLD_JOBS)

                .post("/clean/{filter}")
                .description("Triggers the clean ALL dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations) or stop places")
                .param()
                .required(Boolean.TRUE)
                .name("filter")
                .type(RestParamType.path)
                .description("Optional filter to clean only level 1, level 2 or all spaces (no parameter value)")
                .allowableValues("all", "level1", "level2")
                .endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_CLEAN_ALL)

                .post("/stop_places/clean")
                .description("Triggers the cleaning of ALL stop places in Chouette")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_CLEAN_STOP_PLACES)

                .get("/line_statistics/{filter}")
                .description("List stats about data in chouette for multiple providers")
                .param().name("providerIds")
                .type(RestParamType.query).dataType(INTEGER)
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
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_STATS_MULTIPLE_PROVIDERS)

                .post("/line_statistics/refresh")
                .description("Recalculate stats about data in chouette for all providers")
                .bindingMode(RestBindingMode.off)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_STATS_REFRESH_CACHE)

                .get("/export/files")
                .description("List files containing exported time table data and graphs")
                .outType(BlobStoreFiles.class)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_TIMETABLE_FILES_GET)


                .get("/export/files/{providerId}")
                .description("List files containing exported time table data and graphs for specified providerId")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .outType(BlobStoreFiles.class)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_TIMETABLE_FILES_GET_PROVIDER)


                .post("/export/gtfs/extended")
                .description("Prepare and upload GTFS extended export")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_TIMETABLE_GTFS_EXTENDED_EXPORT)


                .post("/export/netex/merged")
                .description("Prepare and upload a merged Netex file for Norway")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INTERNAL_ERROR).endResponseMessage()
                .to(ROUTE_ADMIN_TIMETABLE_NETEX_MERGED_EXPORT)

                .get(SWAGGER_ENDPOINT)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .to(commonApiDocEndpoint);


        rest("/timetable_admin/{providerId}")
                .post("/import")
                .description("Triggers the import->validate->export process in Chouette for each blob store file handle. Use /files call to obtain available files. Files are imported in the same order as they are provided")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .type(BlobStoreFiles.class)
                .outType(String.class)
                .consumes(MediaType.APPLICATION_JSON)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message("Job accepted").endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_IMPORT)

                .post("/import/{importConfigurationId}")
                .description("Triggers a predefined import.")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message("Command for predefined import accepted").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_IMPORT_ALL)

                .get("/files")
                .description("List files available for reimport into Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description("Provider id as obtained from the baba service").dataType(INTEGER).endParam()
                .outType(BlobStoreFiles.class)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_IMPORT_LIST)

                .post("/analyzeFile")
                .description("Upload file for pre-import analyze into Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.MULTIPART_FORM_DATA)
                .produces(MediaType.APPLICATION_JSON)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_UPLOAD_FILE_TO_ANALYSIS)


                .post("/files")
                .description("Upload file for import into Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(MediaType.TEXT_PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_UPLOAD_FILE)

                .get("/files/{fileName}")
                .description("Download file for reimport into Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param().name(FILENAME).type(RestParamType.path).description("Name of file to fetch").dataType("string").endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid fileName").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_FILE_DOWNLOAD)


                .get("/files/stop-places")
                .description("Download stop places export file (NeTEx stop places)")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_STOP_PLACES_FILE_DOWNLOAD)

                .get("/files/offer/{jobId}")
                .description("Download offer export file (GTFS, NeTEx or Concerto")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param().name(JOB_ID).type(RestParamType.path).description("Job id").dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId or jobId").endResponseMessage()
                .to(ROUTE_ADMIN_OFFER_FILE_DOWNLOAD)

                .get("/line_statistics")
                .description("List stats about data in chouette for a given provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .bindingMode(RestBindingMode.off)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_STATS)

                .get(JOBS_ENDPOINT)
                .description("List Chouette jobs for a given provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param()
                .required(Boolean.FALSE)
                .name(STATUS)
                .type(RestParamType.query)
                .description("Chouette job statuses")
                .allowableValues(Arrays.stream(Status.values()).map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name(ACTION)
                .type(RestParamType.query)
                .description("Chouette job types")
                .allowableValues(IMPORTER, EXPORTER, VALIDATOR)
                .endParam()
                .outType(JobResponse[].class)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_LIST_JOBS)

                .get("/tiamat-jobs")
                .description("List Tiamat jobs for a given provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param()
                .required(Boolean.FALSE)
                .name(STATUS)
                .type(RestParamType.query)
                .description("Tiamat job statuses")
                .allowableValues(Arrays.stream(Status.values()).map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name(ACTION)
                .type(RestParamType.query)
                .description("Tiamat job types")
                .allowableValues(IMPORTER)
                .endParam()
                .outType(JobResponse[].class)
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message(INVALID_PROVIDER).endResponseMessage()
                .to(ROUTE_ADMIN_TIAMAT_LIST_JOBS)

                .delete(JOBS_ENDPOINT)
                .description("Cancel all Chouette jobs for a given provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message("Job deleted").endResponseMessage()
                .responseMessage().code(500).message("Invalid jobId").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_CANCEL_ALL_JOBS)

                .delete("/jobs/{jobId}")
                .description("Cancel a Chouette job for a given provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param().name(JOB_ID).type(RestParamType.path).description("Job id as returned in any of the /jobs GET calls").dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message("Job deleted").endResponseMessage()
                .responseMessage().code(500).message("Invalid jobId").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_CANCEL_JOB)

                .post("/export")
                .description("Triggers the export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT)

                .post("/export/netex")
                .description("Triggers the Netex export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT_NETEX)

                .post("/export/neptune")
                .description("Triggers the neptune export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT_NEPTUNE)

                .post("/export/netex_simulation")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_SIMULATION_EXPORT_NETEX)

                .post("/export/gtfs")
                .description("Triggers the Gtfs export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT_GTFS)

                .post("/export/all")
                .description("Triggers all exports process in Chouette.")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message("Command for all exports accepted").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT_ALL)

                .post("/export-by-id/{exportConfigurationId}")
                .description("Triggers export by id process in Chouette.")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message("Command for export accepted").endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT_BY_ID)

                .post("/export/concerto")
                .description("Triggers the Concerto export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_EXPORT_CONCERTO)

                .post("/export/stops")
                .description("Triggers the stops export process in Tiamat. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_TIAMAT_EXPORT_STOPS)

                .post("/export/parkings")
                .description("Triggers the parkings export process in Tiamat. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_TIAMAT_EXPORT_PARKINGS)

                .post("/export/poi")
                .description("Triggers the poi export process in Tiamat. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_TIAMAT_EXPORT_POI)

                .post("/export/netexFares")
                .description("Triggers the NETEX FARES export process in FARES. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_FARES_EXPORT_NETEX_FARES)

                .post("/validate")
                .description("Triggers the validate->export process in Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.APPLICATION_JSON)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_VALIDATE)

                .get("/validate/schedule")
                .outType(ChouetteValidationSchedule.class)
                .description("Get next the validate->export process in Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .produces(MediaType.APPLICATION_JSON)
                .to(ROUTE_ADMIN_GET_CHOUETTE_VALIDATE_SCHEDULE)

                .post("/validate/schedule")
                .type(ChouetteValidationSchedule.class)
                .description("Schedule manual validate->export process in Chouette")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.APPLICATION_JSON)
                .responseMessage(200, COMMAND_ACCEPTED)
                .to(ROUTE_ADMIN_POST_CHOUETTE_VALIDATE_SCHEDULE)

                .get("/export/netexFares/schedule")
                .description("Schedule manual FARES export for provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .produces(MediaType.APPLICATION_JSON)
                .to(ROUTE_ADMIN_GET_FARES_EXPORT_SCHEDULE)

                .post("/export/netexFares/schedule")
                .type(ExportSchedule.class)
                .description("Schedule manual FARES export for provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.APPLICATION_JSON)
                .responseMessage(200, COMMAND_ACCEPTED)
                .to(ROUTE_ADMIN_POST_FARES_EXPORT_SCHEDULE)

                .get("/predefinedExport/netexFares/{exportConfigurationId}/schedule")
                .description("Retrieve QUARTZ CRON trigger (if it exists) for NETEX fares export")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param().name(EXPORT_CONFIGURATION_ID).type(RestParamType.path).description("Export configuration id").dataType(INTEGER).endParam()
                .to(ROUTE_ADMIN_GET_PREDEFINED_FARES_EXPORT_SCHEDULE)

                .post("/predefinedExport/netexFares/{exportConfigurationId}/schedule")
                .description("Schedule predefined FARES export for provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param().name(EXPORT_CONFIGURATION_ID).type(RestParamType.path).description("Export configuration id").dataType(INTEGER).endParam()
                .consumes(MediaType.APPLICATION_JSON)
                .responseMessage(200, COMMAND_ACCEPTED)
                .to(ROUTE_ADMIN_POST_PREDEFINED_FARES_EXPORT_SCHEDULE)

                .delete("/predefinedExport/netexFares/{exportConfigurationId}/schedule")
                .description("Delete scheduled predefined FARES export for provider")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .param().name(EXPORT_CONFIGURATION_ID).type(RestParamType.path).description("Export configuration id").dataType(INTEGER).endParam()
                .responseMessage(200, COMMAND_ACCEPTED)
                .to(ROUTE_ADMIN_DELETE_PREDEFINED_FARES_EXPORT_SCHEDULE)

                .post("/delete-exports")
                .description("Delete all exports linked to provider")
                .responseMessage().code(200).message("Delete exports command accepted").endResponseMessage()
                .to(ROUTE_ADMIN_DELETE_EXPORTS)

                .post("/clean")
                .description("Triggers the clean dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations)")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_CLEAN)

                .post("/transfer")
                .description("Triggers transfer of data from one dataspace to the next")
                .param().name(PROVIDER).type(RestParamType.path).description(PROVIDER_DESCRIPTION).dataType(INTEGER).endParam()
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_CHOUETTE_TRANSFER)

                .post("/update-scheduler-import-configuration")
                .description("Update scheduler for the import configuration process.")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_IMPORT_CONFIGURATION_SCHEDULER)

                .get("/get-cron/{importConfigurationId}")
                .description("Get cron import configuration")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_GET_CRON_IMPORT_CONFIGURATION_SCHEDULER)

                .post("/delete-scheduler-import-configuration/{importConfigurationId}")
                .description("Delete scheduler import configuration process.")
                .responseMessage().code(200).message("Delete scheduler import configuration command accepted").endResponseMessage()
                .to(ROUTE_ADMIN_DELETE_IMPORT_CONFIGURATION_SCHEDULER)

                .post("/update-scheduler-validation-export")
                .description("Update scheduler validation and export process.")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_VALIDATION_EXPORT_SCHEDULER)

                .get("/get-cron-validation-export/{importConfigurationId}")
                .description("Get cron for validation and export")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.APPLICATION_JSON)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_GET_CRON_VALIDATION_EXPORT_SCHEDULER)

                .post("/delete-scheduler-validation-export/{importConfigurationId}")
                .description("Delete scheduler validation and export.")
                .responseMessage().code(200).message("Delete scheduler validation and export command accepted").endResponseMessage()
                .to(ROUTE_ADMIN_DELETE_VALIDATION_EXPORT_SCHEDULER);

        declareTimeTableAdminRoute();


        rest("/map_admin")
                .post("/download")
                .description("Triggers downloading of the latest OSM data")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_FETCH_OSM)

                .post("/mapbox_update")
                .description("Triggers update of mapbox tileset from tiamat data")
                .consumes(MediaType.TEXT_PLAIN)
                .produces(MediaType.TEXT_PLAIN)
                .responseMessage().code(200).message(COMMAND_ACCEPTED).endResponseMessage()
                .to(ROUTE_ADMIN_UPDATE_MAPBOX)

                .get(SWAGGER_ENDPOINT)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .to(commonApiDocEndpoint);

        from(ROUTE_AUTHORIZE_REQUEST)
                .doTry()
                .process(e -> authorizationService.verifyAtLeastOne(
                                new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN),
                                new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT, e.getIn().getHeader(PROVIDER_ID, Long.class)),

                                /*
                                 * Hack le temps de régler l'auth avec Lumiplan.
                                 * TODO : nettoyez ça une fois le process d'authentification calé.
                                 */
                                new AuthorizationClaim("ADMINEDITROUTEDATA")
                        )
                )
                .routeId(ROUTE_ID_AUTHORIZE_REQUEST);

        from("direct:launchGlobalNetexExport")
                .setHeader(Exchange.FILE_PARENT, simple(mergedNetexTmpDirectory))
                .setExchangePattern(ExchangePattern.InOnly)
                .to("direct:cleanUpLocalDirectory")
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setExchangePattern(ExchangePattern.InOnly)
                .to("direct:chouetteNetexExportForAllProviders")
                .routeId("launch-global-netex-export");

        declareMapAdminRoute();
        declareTimeTableAdminByProviderRoute();
    }


    public static class ImportFilesSplitter {
        public List<String> splitFiles(@Body BlobStoreFiles files) {
            return files.getFiles().stream().map(File::getName).collect(Collectors.toList());
        }
    }

    private String getHeaders(Exchange e, String headerToCollect) {
        Map body = e.getIn().getBody(Map.class);
        Map headers;
        headers = body == null ? e.getIn().getHeaders() : (Map) body.get(HEADERS);

        if (headers != null) {
            return String.valueOf(headers.get(headerToCollect));
        }
        return null;
    }


    protected String getGenerateMapMatchingHeaders(Exchange e) {
        Map<String, Object> body = e.getIn().getBody(Map.class);
        Map<String, Object> headers;
        headers = body == null ? e.getIn().getHeaders() : (Map<String, Object>) body.get(HEADERS);
        String result = ImportGenerateMapMatching.NONE.name();
        if (headers != null && headers.get(GENERATE_MAP_MATCHING) != null) {
            result = (String) headers.get(GENERATE_MAP_MATCHING);
        }
        return result;
    }

    private String getSimulationExportPrefix(Exchange e) {
        Map headers = (Map) e.getIn().getBody(Map.class).get(HEADERS);

        if (headers != null) {
            return (String) headers.get(EXPORT_SIMULATION_NAME);
        }
        return null;
    }

    private void getFromHeadersForGTFS(Exchange e) {
        Map headers = (Map) e.getIn().getBody(Map.class).get(HEADERS);
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
            if (headers.get(ID_FORMAT) != null) {
                e.getIn().setHeader(ID_FORMAT, headers.get(ID_FORMAT));

                if(headers.get(ID_FORMAT).equals("TRIDENT")){
                    String organization = (String) e.getIn().getHeader(OKINA_REFERENTIAL);
                    e.getIn().setHeader(LINE_ID_PREFIX, organization.toUpperCase());
                }
            }
            if (headers.get(EXPORT_ATTRIBUTIONS) != null) {
                e.getIn().setHeader(EXPORT_ATTRIBUTIONS, headers.get(EXPORT_ATTRIBUTIONS));
            }
            if (headers.get(USE_EXTENDED_GTFS_ROUTE_TYPES) != null) {
                e.getIn().setHeader(USE_EXTENDED_GTFS_ROUTE_TYPES, headers.get(USE_EXTENDED_GTFS_ROUTE_TYPES));
            }
            if (headers.get(FARES_INCLUDED_HEADER) != null) {
                e.getIn().setHeader(FARES_INCLUDED_HEADER, headers.get(FARES_INCLUDED_HEADER));
            }
            if (headers.get(FLEX_INCLUDED_HEADER) != null) {
                e.getIn().setHeader(FLEX_INCLUDED_HEADER, headers.get(FLEX_INCLUDED_HEADER));
            }
            if (headers.get(GOOGLE_MAPS_COMPATIBILITY) != null) {
                e.getIn().setHeader(GOOGLE_MAPS_COMPATIBILITY, headers.get(GOOGLE_MAPS_COMPATIBILITY));
            }
            if (headers.get(ID_SUFFIX) != null) {
                e.getIn().setHeader(ID_SUFFIX, headers.get(ID_SUFFIX));
            }
            if (headers.get(COMMERCIAL_POINT_ID_PREFIX) != null) {
                e.getIn().setHeader(COMMERCIAL_POINT_ID_PREFIX, headers.get(COMMERCIAL_POINT_ID_PREFIX));
            }
            if (headers.get(COMMERCIAL_POINT_EXPORT) != null) {
                e.getIn().setHeader(COMMERCIAL_POINT_EXPORT, headers.get(COMMERCIAL_POINT_EXPORT));
            }
            if (headers.get(EXPORTED_FILENAME) != null) {
                e.getIn().setHeader(EXPORTED_FILENAME, headers.get(EXPORTED_FILENAME));
            }
            if (headers.get(STOP_ID_PREFIX) != null) {
                e.getIn().setHeader(STOP_ID_PREFIX, headers.get(STOP_ID_PREFIX));
            }
            if (headers.get(LINE_ID_PREFIX) != null) {
                e.getIn().setHeader(LINE_ID_PREFIX, headers.get(LINE_ID_PREFIX));
            }
        }
    }

    private void declareTimeTableAdminRoute() {
        from(ROUTE_ADMIN_APPLICATION_CLEAN_UNIQUE_FILENAME_AND_DIGEST_IDEMPOTENT_REPOS)
                .routeId(ROUTE_ID_ADMIN_APPLICATION_CLEAN_UNIQUE_FILENAME_AND_DIGEST_IDEMPOTENT_REPOS)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .to("direct:cleanIdempotentFileStore")
                .setBody(constant((Object) null));

        from(ROUTE_PROCESS_VALIDATE_LEVEL_1)
                .routeId(ROUTE_ID_PROCESS_VALIDATE_LEVEL_1)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation level1 for all providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("direct:chouetteValidateLevel1ForAllProviders")
                .setBody(constant((Object) null));

        from(ROUTE_PROCESS_VALIDATE_LEVEL_2)
                .routeId(ROUTE_ID_PROCESS_VALIDATE_LEVEL_2)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation level2 for all providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("direct:chouetteValidateLevel2ForAllProviders")
                .setBody(constant((Object) null));

        from(ROUTE_ADMIN_LIST_ALL_CHOUETTE_JOBS)
                .routeId(ROUTE_ID_ADMIN_LIST_ALL_CHOUETTE_JOBS)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.DEBUG, correlation() + "Get chouette active jobs all providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(STATUS, e.getIn().getHeader(STATUS) != null ? e.getIn().getHeader(STATUS) : Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:chouetteGetJobsAll");

        from(ROUTE_ADMIN_CHOUETTE_CANCEL_ALL_JOBS_ALL)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_CANCEL_ALL_JOBS_ALL)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs for all providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteCancelAllJobsForAllProviders")
                .setBody(constant((Object) null));

        from(ROUTE_ADMIN_TIAMAT_CANCEL_ALL_JOBS).process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .routeId(ROUTE_ID_ADMIN_TIAMAT_CANCEL_ALL_JOBS)
                .log(LoggingLevel.INFO, correlation() + "Cancel all tiamat jobs for all providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:tiamatCancelAllJobsForAllProviders")
                .setBody(constant((Object) null));

        from(ROUTE_ADMIN_CHOUETTE_REMOVE_OLD_JOBS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_REMOVE_OLD_JOBS)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Removing old chouette jobs for all providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteRemoveOldJobs")
                .setBody(constant((Object) null));

        from(ROUTE_ADMIN_CHOUETTE_CLEAN_ALL)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_CLEAN_ALL)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette clean all dataspaces")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteCleanAllReferentials")
                .setBody(constant((Object) null))
        ;

        from(ROUTE_ADMIN_CHOUETTE_CLEAN_STOP_PLACES)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_CLEAN_STOP_PLACES)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette clean all stop places")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteCleanStopPlaces")
                .setBody(constant((Object) null));

        from(ROUTE_ADMIN_CHOUETTE_STATS_MULTIPLE_PROVIDERS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_STATS_MULTIPLE_PROVIDERS)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, correlation() + "get stats for multiple providers")
                .removeHeaders(ALL_CAMEL_HTTP)
                .choice()
                .when(simple("${header.providerIds}"))
                .process(e -> e.getIn().setHeader(PROVIDER_IDS, e.getIn().getHeader("providerIds", "", String.class).split(",")))
                .end()
                .to("direct:chouetteGetStats");

        from(ROUTE_ADMIN_CHOUETTE_STATS_REFRESH_CACHE)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_STATS_REFRESH_CACHE)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, correlation() + "refresh stats cache")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteRefreshStatsCache");

        from(ROUTE_ADMIN_CHOUETTE_TIMETABLE_FILES_GET)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_TIMETABLE_FILES_GET)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, correlation() + "get time table and graph files")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:listTimetableExportAndGraphBlobs");

        from(ROUTE_ADMIN_CHOUETTE_TIMETABLE_FILES_GET_PROVIDER)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_TIMETABLE_FILES_GET_PROVIDER)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "get time table and graph files")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:listTimetableExportAndGraphBlobsByProvider");

        from(ROUTE_ADMIN_TIMETABLE_GTFS_EXTENDED_EXPORT)
                .routeId(ROUTE_ID_ADMIN_TIMETABLE_GTFS_EXTENDED_EXPORT)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, "Triggered GTFS extended export")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:GtfsExportMergedQueue");


        from(ROUTE_ADMIN_TIMETABLE_GTFS_BASIC_EXPORT)
                .routeId(ROUTE_ID_ADMIN_TIMETABLE_GTFS_BASIC_EXPORT)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, "Triggered GTFS basic export")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:GtfsBasicExportMergedQueue");

        from(ROUTE_ADMIN_TIMETABLE_NETEX_MERGED_EXPORT)
                .routeId(ROUTE_ID_ADMIN_TIMETABLE_NETEX_MERGED_EXPORT)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, "Triggered Netex export of merged file for Norway")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:NetexExportMergedQueue");

    }

    public void declareTimeTableAdminByProviderRoute() {
        from(ROUTE_ADMIN_CHOUETTE_IMPORT)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_IMPORT)
                .removeHeaders(ALL_CAMEL_HTTP)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(IMPORT, constant(true))
                .to(ROUTE_AUTHORIZE_REQUEST)
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
                .setBody(constant((Object) null))
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ROUTE_PROCESS_FILE_QUEUE);

        from(ROUTE_ADMIN_CHOUETTE_IMPORT_ALL)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_IMPORT_ALL)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(IMPORT_CONFIGURATION_ID, header(IMPORT_CONFIGURATION_ID))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start import predefined")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:ImportConfigurationQueue");

        from(ROUTE_ADMIN_CHOUETTE_IMPORT_LIST)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_IMPORT_LIST)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "blob store get files")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:listBlobsFlat");

        from(ROUTE_ADMIN_CHOUETTE_UPLOAD_FILE_TO_ANALYSIS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_UPLOAD_FILE_TO_ANALYSIS)
                .streamCache(Boolean.TRUE)
                .setBody(simple("${exchange.getIn().getRequest().getFileMap()}"))
                .split().body()
                .process(multiPartProcessor)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .log(LoggingLevel.INFO, "Authorized request passed")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, "Validation passed")
                .process(e -> e.getIn().setHeader(ANALYZE_ACTION, true))
                .log(LoggingLevel.INFO, correlation() + "upload files and start import pipeline")
                .removeHeaders(ALL_CAMEL_HTTP)
                .doTry()
                .process(fileValidationProcessor)
                .to(ROUTE_UPLOAD_FILES_AND_START_IMPORT)
                .doCatch(Exception.class)
                .process(e -> {
                    Exception exception = e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    e.getIn().setHeader(HTTP_RESPONSE_CODE, 500);
                    Map<String, String> result = new HashMap<>();
                    result.put("message", exception.getMessage());
                    e.getIn().setBody(result);
                })
                .marshal().json(JsonLibrary.Jackson)
                .end();

        from(ROUTE_ADMIN_CHOUETTE_UPLOAD_FILE)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_UPLOAD_FILE)
                .streamCache(Boolean.TRUE)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .process(e -> log.info("Authorized request passed"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> log.info("validation passed"))
                .process(e -> {
                    String referential = getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    String jobId = e.getIn().getHeader(ANALYSIS_JOB_ID, String.class);
                    java.io.File gtfsZipFile = fileSystemService.getImportZipFileByReferentialAndJobId(referential, jobId);
                    e.getIn().setBody(gtfsZipFile);
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, referential);
                    e.getIn().setHeader(FILE_NAME, gtfsZipFile.getName());
                    e.getIn().setHeader(GENERATE_MAP_MATCHING, getGenerateMapMatchingHeaders(e));
                })
                .log(LoggingLevel.INFO, correlation() + "upload files and start import pipeline")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to(ROUTE_IMPORT_LAUNCH);

        from(ROUTE_ADMIN_CHOUETTE_FILE_DOWNLOAD)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_FILE_DOWNLOAD)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> e.getIn().setHeader(FILENAME, URLDecoder.decode(e.getIn().getHeader(FILENAME, String.class), StandardCharsets.UTF_8)))
                .process(e -> e.getIn().setHeader(FILE_HANDLE, BLOBSTORE_PATH_INBOUND
                        + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
                        + "/" + e.getIn().getHeader(FILENAME, String.class)))
                .log(LoggingLevel.INFO, correlation() + "blob store download file by name")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:getBlob")
                .choice().when(simple(BODY_IS_NULL)).setHeader(HTTP_RESPONSE_CODE, constant(404)).endChoice();

        from(ROUTE_ADMIN_STOP_PLACES_FILE_DOWNLOAD)
                .routeId(ROUTE_ID_ADMIN_STOP_PLACES_FILE_DOWNLOAD)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader("Access-Control-Expose-Headers", simple(FILE_NAME))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> {
                    String ref = e.getIn().getHeader(OKINA_REFERENTIAL, String.class);
                    if (!ref.contains(superspaceName + "_")) {
                        e.getIn().setHeader(OKINA_REFERENTIAL, superspaceName + "_" + ref);
                    } else {
                        e.getIn().setHeader(OKINA_REFERENTIAL, ref);
                    }
                })
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:getStopPlacesFile")
                .choice().when(simple(BODY_IS_NULL)).setHeader(HTTP_RESPONSE_CODE, constant(404)).endChoice();

        from(ROUTE_ADMIN_OFFER_FILE_DOWNLOAD)
                .routeId(ROUTE_ID_ADMIN_OFFER_FILE_DOWNLOAD)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(Headers.JOB_ID, header(JOB_ID))
                .setHeader("Access-Control-Expose-Headers", simple(FILE_NAME))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> {
                    String ref = e.getIn().getHeader(OKINA_REFERENTIAL, String.class);
                    if (!ref.contains(superspaceName + "_") && !ref.startsWith(simulationName + "_")) {
                        e.getIn().setHeader(OKINA_REFERENTIAL, superspaceName + "_" + ref);
                    } else {
                        e.getIn().setHeader(OKINA_REFERENTIAL, ref);
                    }
                })
                .removeHeaders(ALL_CAMEL_HTTP)
                .removeHeaders("Authorization*")
                .to("direct:getOfferFile")
                .choice().when(simple(BODY_IS_NULL)).setHeader(HTTP_RESPONSE_CODE, constant(404)).endChoice();

        from(ROUTE_ADMIN_CHOUETTE_STATS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_STATS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "get stats")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteGetStatsSingleProvider");

        from(ROUTE_ADMIN_CHOUETTE_LIST_JOBS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_LIST_JOBS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Get chouette jobs status=${header.status} action=${header.action}")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteGetJobsForProvider");

        from(ROUTE_ADMIN_TIAMAT_LIST_JOBS)
                .routeId(ROUTE_ID_ADMIN_TIAMAT_LIST_JOBS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Get timat jobs status=${header.status} action=${header.action}")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:tiamatGetJobsForProvider");

        from(ROUTE_ADMIN_CHOUETTE_CANCEL_ALL_JOBS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_CANCEL_ALL_JOBS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteCancelAllJobsForProvider");

        from(ROUTE_ADMIN_CHOUETTE_CANCEL_JOB)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_CANCEL_JOB)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(Headers.JOB_ID, header(JOB_ID))
                .log(LoggingLevel.INFO, correlation() + "Cancel chouette job")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteCancelJob");

        from(ROUTE_ADMIN_CHOUETTE_EXPORT)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(NO_GTFS_EXPORT, constant(false))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(false))
                .setHeader(IS_SIMULATION_EXPORT, constant(false))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE);

        from(ROUTE_ADMIN_CHOUETTE_EXPORT_NETEX)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT_NETEX)
                .process(e -> e.getIn().setHeader(EXPORT_GENERATED_MISSING_QUAYS, getHeaders(e, EXPORT_GENERATED_MISSING_QUAYS)))
                .process(e -> e.getIn().setHeader(EXPORT_EXTERNAL_IDS, getHeaders(e, EXPORT_EXTERNAL_IDS)))
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(NO_GTFS_EXPORT, constant(true))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(false))
                .setHeader(IS_SIMULATION_EXPORT, constant(false))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Netex")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .marshal().json(JsonLibrary.Jackson)
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE);

        from(ROUTE_ADMIN_CHOUETTE_EXPORT_NEPTUNE)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT_NEPTUNE)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(NO_GTFS_EXPORT, constant(true))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(false))
                .setHeader(IS_SIMULATION_EXPORT, constant(false))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Neptune")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:ChouetteExportNeptuneQueue");

        from(ROUTE_SIMULATION_EXPORT_NETEX)
                .routeId(ROUTE_ID_SIMULATION_EXPORT_NETEX)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(NO_GTFS_EXPORT, constant(true))
                .setHeader(NETEX_EXPORT_GLOBAL, constant(false))
                .setHeader(IS_SIMULATION_EXPORT, constant(true))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(ROLE_EXPORT_SIMULATION)))
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> {
                    e.getIn().setHeader(USER, getHeaders(e, USER));
                    e.getIn().setHeader(EXPORT_SIMULATION_NAME, getSimulationExportPrefix(e));
                })
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE);

        from(ROUTE_ADMIN_CHOUETTE_EXPORT_GTFS)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT_GTFS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(GTFS_EXPORT_GLOBAL, constant(false))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export GTFS")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(this::getFromHeadersForGTFS)
                .marshal().json(JsonLibrary.Jackson)
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:ChouetteExportGtfsQueue");

        from(ROUTE_ADMIN_CHOUETTE_EXPORT_ALL)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT_ALL)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start all export process")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:predefinedExports");

        from(ROUTE_ADMIN_CHOUETTE_EXPORT_BY_ID)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT_BY_ID)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .setHeader(EXPORT_CONFIGURATION_ID, header("exportConfigurationId"))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export process")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:predefinedExport");

        from(ROUTE_ADMIN_CHOUETTE_EXPORT_CONCERTO)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_EXPORT_CONCERTO)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export Concerto")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:ChouetteExportConcertoQueue");

        from(ROUTE_ADMIN_TIAMAT_EXPORT_STOPS)
                .routeId(ROUTE_ID_ADMIN_TIAMAT_EXPORT_STOPS)
                .process(e -> e.getIn().setHeader(EXPORT_GENERATED_MISSING_QUAYS, getHeaders(e, EXPORT_GENERATED_MISSING_QUAYS)))
                .process(e -> e.getIn().setHeader(EXPORT_EXTERNAL_IDS, getHeaders(e, EXPORT_EXTERNAL_IDS)))
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Tiamat start export Stops")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .marshal().json(JsonLibrary.Jackson)
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:TiamatStopPlacesExport");

        from(ROUTE_ADMIN_TIAMAT_EXPORT_PARKINGS)
                .routeId(ROUTE_ID_ADMIN_TIAMAT_EXPORT_PARKINGS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Tiamat start export Parkings")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:TiamatParkingsExport");

        from(ROUTE_ADMIN_TIAMAT_EXPORT_POI)
                .routeId(ROUTE_ID_ADMIN_TIAMAT_EXPORT_POI)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .process(e -> log.info("providerId: {}", e.getIn().getHeader(PROVIDER_ID, Long.class)))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Tiamat start export POI")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody().simple(CAMEL_HEADERS)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:TiamatPointOfInterestExport");

        from(ROUTE_ADMIN_FARES_EXPORT_NETEX_FARES)
                .routeId(ROUTE_ID_ADMIN_FARES_EXPORT_NETEX_FARES)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "FARES start export NETEX FARES")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .setBody(simple(null))
                .bean(userActionsLoggingService, "recordFaresExport")
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:exportNetexFaresQueue");

        from(ROUTE_ADMIN_CHOUETTE_VALIDATE)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_VALIDATE)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .to(ROUTE_AUTHORIZE_REQUEST)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation")
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setHeader(USER, getHeaders(e, USER)))
                .choice()
                .when(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.migrateDataToProvider == null)
                .setHeader(JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_2.name()))
                .otherwise()
                .setHeader(JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name()))
                .end()
                .setBody().simple(CAMEL_HEADERS)
                .bean(userActionsLoggingService, "recordValidation")
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:ChouetteValidationQueue");

        from(ROUTE_ADMIN_GET_CHOUETTE_VALIDATE_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_GET_CHOUETTE_VALIDATE_SCHEDULE)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .log(LoggingLevel.INFO, correlation() + "Retrieve next scheduled chouette validation")
                .process(e -> {
                    ChouetteValidationSchedule schedule = new ChouetteValidationSchedule();
                    schedule.setValidationSchedule(chouetteValidationScheduleService.getNextManualValidationScheduledForProvider(e.getIn().getHeader(PROVIDER, Long.class)));
                    e.getIn().setBody(schedule);
                })
                .end();

        from(ROUTE_ADMIN_POST_CHOUETTE_VALIDATE_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_POST_CHOUETTE_VALIDATE_SCHEDULE)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> chouetteValidationScheduleService.scheduleManualValidationForProvider(
                        e.getIn().getHeader(PROVIDER, Long.class),
                        e.getIn().getBody(ChouetteValidationSchedule.class).getValidationSchedule()))
                .end();

        from(ROUTE_ADMIN_GET_FARES_EXPORT_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_GET_FARES_EXPORT_SCHEDULE)
                .log(LoggingLevel.INFO, "Get next NeTeX fares export schedule")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> {
                    ExportSchedule schedule = new ExportSchedule();
                    schedule.setWhen(faresScheduleService.getNextScheduledFaresExportForProvider((e.getIn().getHeader(PROVIDER, Long.class))));
                    e.getIn().setBody(schedule);
                })
                .end();

        from(ROUTE_ADMIN_POST_FARES_EXPORT_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_POST_FARES_EXPORT_SCHEDULE)
                .log(LoggingLevel.INFO, "Scheduling NeTeX fares export")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> faresScheduleService.scheduleManualFaresExportForProvider(
                        e.getIn().getHeader(PROVIDER, Long.class),
                        e.getIn().getBody(ExportSchedule.class).getWhen(),
                        e.getIn().getHeader(USER, String.class)))
                .end();

        from(ROUTE_ADMIN_GET_PREDEFINED_FARES_EXPORT_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_GET_PREDEFINED_FARES_EXPORT_SCHEDULE)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> e.getIn().setBody(
                        faresScheduleService.getPredefinedFaresExportCronExpressionByProviderByExportConfigurationId(
                                e.getIn().getHeader(PROVIDER, Long.class),
                                e.getIn().getHeader(EXPORT_CONFIGURATION_ID, Long.class)))
                ).end();

        from(ROUTE_ADMIN_POST_PREDEFINED_FARES_EXPORT_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_POST_PREDEFINED_FARES_EXPORT_SCHEDULE)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> faresScheduleService.schedulePredefinedFaresExportForProvider(
                        e.getIn().getHeader(PROVIDER, Long.class),
                        e.getIn().getHeader(EXPORT_CONFIGURATION_ID, Long.class),
                        e.getIn().getHeader(CRON_EXPRESSION, String.class)))
                .end();

        from(ROUTE_ADMIN_DELETE_PREDEFINED_FARES_EXPORT_SCHEDULE)
                .routeId(ROUTE_ID_ADMIN_DELETE_PREDEFINED_FARES_EXPORT_SCHEDULE)
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER, Long.class)) != null)
                .removeHeaders(ALL_CAMEL_HTTP)
                .process(e -> faresScheduleService.unschedulePredefinedFaresExportForProvider(
                        e.getIn().getHeader(PROVIDER, Long.class),
                        e.getIn().getHeader(EXPORT_CONFIGURATION_ID, Long.class)))
                .end();

        from(ROUTE_ADMIN_DELETE_EXPORTS)
                .routeId(ROUTE_ID_ADMIN_DELETE_EXPORTS)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .process(e -> {
                    log.info("Delete exports starting");
                    Provider provider = getProviderRepository().getNonMobiitiProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))
                            .orElseThrow(() -> new RuntimeException("No valid base provider found. Provider id : " + e.getIn().getHeader(PROVIDER_ID)));
                    String baseProviderFolder = BlobStoreRoute.exportSiteId(provider);
                    blobStoreService.deleteAllBlobsInFolder(baseProviderFolder, e);
                    blobStoreService.deleteBlob(baseProviderFolder, e);
                    log.info("Delete exports done");
                });

        from(ROUTE_ADMIN_CHOUETTE_CLEAN)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_CLEAN)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette clean dataspace")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:chouetteCleanReferential");

        from(ROUTE_ADMIN_CHOUETTE_TRANSFER)
                .routeId(ROUTE_ID_ADMIN_CHOUETTE_TRANSFER)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .log(LoggingLevel.INFO, correlation() + "Chouette transfer dataspace")
                .removeHeaders(ALL_CAMEL_HTTP)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setExchangePattern(ExchangePattern.InOnly)
                .to("jms:queue:ChouetteTransferExportQueue");

        from(ROUTE_ADMIN_IMPORT_CONFIGURATION_SCHEDULER)
                .routeId(ROUTE_ID_ADMIN_IMPORT_CONFIGURATION_SCHEDULER)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Update scheduler for the import configuration")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:updateSchedulerImportConfiguration");

        from(ROUTE_ADMIN_GET_CRON_IMPORT_CONFIGURATION_SCHEDULER)
                .routeId(ROUTE_ID_ADMIN_GET_CRON_IMPORT_CONFIGURATION_SCHEDULER)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(IMPORT_CONFIGURATION_ID, header(IMPORT_CONFIGURATION_ID))
                .log(LoggingLevel.INFO, correlation() + "Get cron from scheduler for the import configuration")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:getCron");

        from(ROUTE_ADMIN_DELETE_IMPORT_CONFIGURATION_SCHEDULER)
                .routeId(ROUTE_ID_ADMIN_DELETE_IMPORT_CONFIGURATION_SCHEDULER)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(IMPORT_CONFIGURATION_ID, header(IMPORT_CONFIGURATION_ID))
                .log(LoggingLevel.INFO, correlation() + "Delete scheduler import configuration")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:deleteSchedulerImportConfiguration");

        from(ROUTE_ADMIN_VALIDATION_EXPORT_SCHEDULER)
                .routeId(ROUTE_ID_ADMIN_VALIDATION_EXPORT_SCHEDULER)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Update scheduler for the import configuration")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:updateSchedulerValidationExport");

        from(ROUTE_ADMIN_GET_CRON_VALIDATION_EXPORT_SCHEDULER)
                .routeId(ROUTE_ID_ADMIN_GET_CRON_VALIDATION_EXPORT_SCHEDULER)
                .to(ROUTE_AUTHORIZE_REQUEST)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(IMPORT_CONFIGURATION_ID, header(IMPORT_CONFIGURATION_ID))
                .log(LoggingLevel.INFO, correlation() + "Get cron from scheduler for the validation & export")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:getCronValidationExport");

        from(ROUTE_ADMIN_DELETE_VALIDATION_EXPORT_SCHEDULER)
                .routeId(ROUTE_ID_ADMIN_DELETE_VALIDATION_EXPORT_SCHEDULER)
                .setHeader(PROVIDER_ID, header(PROVIDER))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(IMPORT_CONFIGURATION_ID, header(IMPORT_CONFIGURATION_ID))
                .log(LoggingLevel.INFO, correlation() + "Delete scheduler import configuration")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:deleteSchedulerValidationExport");
    }

    public void declareMapAdminRoute() {
        from(ROUTE_ADMIN_FETCH_OSM)
                .routeId(ROUTE_ID_ADMIN_FETCH_OSM)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "OSM update map data")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:considerToFetchOsmMapOverNorway");

        from(ROUTE_ADMIN_UPDATE_MAPBOX)
                .routeId(ROUTE_ID_ADMIN_UPDATE_MAPBOX)
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "Mapbox update with data from tiamat")
                .removeHeaders(ALL_CAMEL_HTTP)
                .to("direct:runMapboxUpdate");
    }

}


