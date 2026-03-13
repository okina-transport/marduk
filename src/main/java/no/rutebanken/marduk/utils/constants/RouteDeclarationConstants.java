package no.rutebanken.marduk.utils.constants;

public class RouteDeclarationConstants {

    private RouteDeclarationConstants() {
        throw new IllegalArgumentException();
    }

    public static final String DIRECT_ROUTE_PREFIX = "direct:";
    public static final String DIRECT_JMS_ROUTE_PREFIX = "jms:queue:";

    public static final String ROUTE_ID_AUTHORIZE_REQUEST = "authorizeRequest";
    public static final String ROUTE_AUTHORIZE_REQUEST = DIRECT_ROUTE_PREFIX + ROUTE_ID_AUTHORIZE_REQUEST;

    public static final String ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_GET = "admin-route-authorize-get";
    public static final String ROUTE_ADMIN_ROUTE_AUTHORIZE_GET = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_GET;
    public static final String ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_POST = "admin-route-authorize-post";
    public static final String ROUTE_ADMIN_ROUTE_AUTHORIZE_POST = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_POST;
    public static final String ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_PUT = "admin-route-authorize-put";
    public static final String ROUTE_ADMIN_ROUTE_AUTHORIZE_PUT = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_PUT;
    public static final String ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_DELETE = "admin-route-authorize-delete";
    public static final String ROUTE_ADMIN_ROUTE_AUTHORIZE_DELETE = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_ROUTE_AUTHORIZE_DELETE;

    public static final String ROUTE_ID_ADMIN_APPLICATION_CLEAN_UNIQUE_FILENAME_AND_DIGEST_IDEMPOTENT_REPOS = "admin-application-clean-unique-filename-and-digest-idempotent-repos";
    public static final String ROUTE_ADMIN_APPLICATION_CLEAN_UNIQUE_FILENAME_AND_DIGEST_IDEMPOTENT_REPOS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_APPLICATION_CLEAN_UNIQUE_FILENAME_AND_DIGEST_IDEMPOTENT_REPOS;

    public static final String ROUTE_ID_PROCESS_VALIDATE_LEVEL_1 = "admin-chouette-validate-level1-all-providers";
    public static final String ROUTE_PROCESS_VALIDATE_LEVEL_1 = DIRECT_ROUTE_PREFIX + ROUTE_ID_PROCESS_VALIDATE_LEVEL_1;

    public static final String ROUTE_ID_PROCESS_VALIDATE_LEVEL_2 = "admin-chouette-validate-level2-all-providers";
    public static final String ROUTE_PROCESS_VALIDATE_LEVEL_2 = DIRECT_ROUTE_PREFIX + ROUTE_ID_PROCESS_VALIDATE_LEVEL_2;

    public static final String ROUTE_ID_ADMIN_LIST_ALL_CHOUETTE_JOBS = "admin-chouette-list-all-jobs";
    public static final String ROUTE_ADMIN_LIST_ALL_CHOUETTE_JOBS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_LIST_ALL_CHOUETTE_JOBS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_CANCEL_ALL_JOBS_ALL = "admin-chouette-cancel-all-jobs-all";
    public static final String ROUTE_ADMIN_CHOUETTE_CANCEL_ALL_JOBS_ALL = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_CANCEL_ALL_JOBS_ALL;

    public static final String ROUTE_ID_ADMIN_TIAMAT_CANCEL_ALL_JOBS = "admin-tiamat-cancel-all-jobs-all";
    public static final String ROUTE_ADMIN_TIAMAT_CANCEL_ALL_JOBS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIAMAT_CANCEL_ALL_JOBS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_REMOVE_OLD_JOBS = "admin-chouette-remove-old-jobs";
    public static final String ROUTE_ADMIN_CHOUETTE_REMOVE_OLD_JOBS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_REMOVE_OLD_JOBS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_CLEAN_ALL = "admin-chouette-clean-all";
    public static final String ROUTE_ADMIN_CHOUETTE_CLEAN_ALL = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_CLEAN_ALL;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_CLEAN_STOP_PLACES = "admin-chouette-clean-stop-places";
    public static final String ROUTE_ADMIN_CHOUETTE_CLEAN_STOP_PLACES = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_CLEAN_STOP_PLACES;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_STATS_MULTIPLE_PROVIDERS = "admin-chouette-stats-multiple-providers";
    public static final String ROUTE_ADMIN_CHOUETTE_STATS_MULTIPLE_PROVIDERS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_STATS_MULTIPLE_PROVIDERS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_STATS_REFRESH_CACHE = "admin-chouette-stats-refresh-cache";
    public static final String ROUTE_ADMIN_CHOUETTE_STATS_REFRESH_CACHE = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_STATS_REFRESH_CACHE;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_TIMETABLE_FILES_GET = "admin-chouette-timetable-files-get";
    public static final String ROUTE_ADMIN_CHOUETTE_TIMETABLE_FILES_GET = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_TIMETABLE_FILES_GET;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_TIMETABLE_FILES_GET_PROVIDER = "admin-chouette-timetable-files-get-provider";
    public static final String ROUTE_ADMIN_CHOUETTE_TIMETABLE_FILES_GET_PROVIDER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_TIMETABLE_FILES_GET_PROVIDER;

    public static final String ROUTE_ID_ADMIN_TIMETABLE_GTFS_EXTENDED_EXPORT = "admin-timetable-gtfs-extended-export";
    public static final String ROUTE_ADMIN_TIMETABLE_GTFS_EXTENDED_EXPORT = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIMETABLE_GTFS_EXTENDED_EXPORT;

    public static final String ROUTE_ID_ADMIN_TIMETABLE_GTFS_BASIC_EXPORT = "admin-timetable-gtfs-basic-export";
    public static final String ROUTE_ADMIN_TIMETABLE_GTFS_BASIC_EXPORT = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIMETABLE_GTFS_BASIC_EXPORT;

    public static final String ROUTE_ID_ADMIN_TIMETABLE_NETEX_MERGED_EXPORT = "admin-timetable-netex-merged-export";
    public static final String ROUTE_ADMIN_TIMETABLE_NETEX_MERGED_EXPORT = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIMETABLE_NETEX_MERGED_EXPORT;

    // BY PROVIDER
    public static final String ROUTE_ID_ADMIN_CHOUETTE_IMPORT = "admin-chouette-import";
    public static final String ROUTE_ADMIN_CHOUETTE_IMPORT = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_IMPORT;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_IMPORT_ALL = "admin-chouette-import-all";
    public static final String ROUTE_ADMIN_CHOUETTE_IMPORT_ALL = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_IMPORT_ALL;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_IMPORT_LIST = "admin-chouette-import-list";
    public static final String ROUTE_ADMIN_CHOUETTE_IMPORT_LIST = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_IMPORT_LIST;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_UPLOAD_FILE_TO_ANALYSIS = "admin-chouette-upload-file-to-analysis";
    public static final String ROUTE_ADMIN_CHOUETTE_UPLOAD_FILE_TO_ANALYSIS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_UPLOAD_FILE_TO_ANALYSIS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_UPLOAD_FILE = "admin-chouette-upload-file";
    public static final String ROUTE_ADMIN_CHOUETTE_UPLOAD_FILE = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_UPLOAD_FILE;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_FILE_DOWNLOAD = "admin-chouette-file-download";
    public static final String ROUTE_ADMIN_CHOUETTE_FILE_DOWNLOAD = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_FILE_DOWNLOAD;

    public static final String ROUTE_ID_ADMIN_STOP_PLACES_FILE_DOWNLOAD = "admin-stop-places-file-download";
    public static final String ROUTE_ADMIN_STOP_PLACES_FILE_DOWNLOAD = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_STOP_PLACES_FILE_DOWNLOAD;

    public static final String ROUTE_ID_ADMIN_OFFER_FILE_DOWNLOAD = "admin-offer-file-download";
    public static final String ROUTE_ADMIN_OFFER_FILE_DOWNLOAD = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_OFFER_FILE_DOWNLOAD;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_STATS = "admin-chouette-stats";
    public static final String ROUTE_ADMIN_CHOUETTE_STATS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_STATS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_LIST_JOBS = "admin-chouette-list-jobs";
    public static final String ROUTE_ADMIN_CHOUETTE_LIST_JOBS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_LIST_JOBS;

    public static final String ROUTE_ID_ADMIN_TIAMAT_LIST_JOBS = "admin-tiamat-list-jobs";
    public static final String ROUTE_ADMIN_TIAMAT_LIST_JOBS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIAMAT_LIST_JOBS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_CANCEL_ALL_JOBS = "admin-chouette-cancel-all-jobs";
    public static final String ROUTE_ADMIN_CHOUETTE_CANCEL_ALL_JOBS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_CANCEL_ALL_JOBS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_CANCEL_JOB = "admin-chouette-cancel-job";
    public static final String ROUTE_ADMIN_CHOUETTE_CANCEL_JOB = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_CANCEL_JOB;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT = "admin-chouette-export";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT_NETEX = "admin-chouette-export-netex";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT_NETEX = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT_NETEX;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT_NEPTUNE = "admin-chouette-export-neptune";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT_NEPTUNE = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT_NEPTUNE;

    public static final String ROUTE_ID_SIMULATION_EXPORT_NETEX = "simulation-export-netex";
    public static final String ROUTE_SIMULATION_EXPORT_NETEX = DIRECT_ROUTE_PREFIX + ROUTE_ID_SIMULATION_EXPORT_NETEX;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT_GTFS = "admin-chouette-export-gtfs";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT_GTFS = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT_GTFS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT_ALL = "admin-chouette-export-all";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT_ALL = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT_ALL;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT_BY_ID = "admin-chouette-export-by-id";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT_BY_ID = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT_BY_ID;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_EXPORT_CONCERTO = "admin-chouette-export-concerto";
    public static final String ROUTE_ADMIN_CHOUETTE_EXPORT_CONCERTO = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_EXPORT_CONCERTO;

    public static final String ROUTE_ID_ADMIN_TIAMAT_EXPORT_STOPS = "admin-tiamat-export-stops";
    public static final String ROUTE_ADMIN_TIAMAT_EXPORT_STOPS =  DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIAMAT_EXPORT_STOPS;

    public static final String ROUTE_ID_ADMIN_TIAMAT_EXPORT_PARKINGS = "admin-tiamat-export-parkings";
    public static final String ROUTE_ADMIN_TIAMAT_EXPORT_PARKINGS =  DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIAMAT_EXPORT_PARKINGS;

    public static final String ROUTE_ID_ADMIN_TIAMAT_EXPORT_POI = "admin-tiamat-export-poi";
    public static final String ROUTE_ADMIN_TIAMAT_EXPORT_POI = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_TIAMAT_EXPORT_POI;

    public static final String ROUTE_ID_ADMIN_FARES_EXPORT_NETEX_FARES = "admin-fares-export-netex-fares";
    public static final String ROUTE_ADMIN_FARES_EXPORT_NETEX_FARES = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_FARES_EXPORT_NETEX_FARES;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_VALIDATE = "admin-chouette-validate";
    public static final String ROUTE_ADMIN_CHOUETTE_VALIDATE = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_VALIDATE;

    public static final String ROUTE_ID_ADMIN_GET_CHOUETTE_VALIDATE_SCHEDULE = "admin-get-chouette-validate-schedule";
    public static final String ROUTE_ADMIN_GET_CHOUETTE_VALIDATE_SCHEDULE = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_GET_CHOUETTE_VALIDATE_SCHEDULE;

    public static final String ROUTE_ID_ADMIN_POST_CHOUETTE_VALIDATE_SCHEDULE = "admin-post-chouette-validate-schedule";
    public static final String ROUTE_ADMIN_POST_CHOUETTE_VALIDATE_SCHEDULE =
            DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_POST_CHOUETTE_VALIDATE_SCHEDULE;

    public static final String ROUTE_ID_ADMIN_DELETE_EXPORTS = "admin-delete-exports";
    public static final String ROUTE_ADMIN_DELETE_EXPORTS =  DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_DELETE_EXPORTS;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_CLEAN = "admin-chouette-clean";
    public static final String ROUTE_ADMIN_CHOUETTE_CLEAN = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_CLEAN;

    public static final String ROUTE_ID_ADMIN_CHOUETTE_TRANSFER = "admin-chouette-transfer";
    public static final String ROUTE_ADMIN_CHOUETTE_TRANSFER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_CHOUETTE_TRANSFER;

    public static final String ROUTE_ID_ADMIN_IMPORT_CONFIGURATION_SCHEDULER = "admin-import-configuration-scheduler";
    public static final String ROUTE_ADMIN_IMPORT_CONFIGURATION_SCHEDULER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_IMPORT_CONFIGURATION_SCHEDULER;

    public static final String ROUTE_ID_ADMIN_GET_CRON_IMPORT_CONFIGURATION_SCHEDULER = "admin-get-cron-import-configuration-scheduler";
    public static final String ROUTE_ADMIN_GET_CRON_IMPORT_CONFIGURATION_SCHEDULER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_GET_CRON_IMPORT_CONFIGURATION_SCHEDULER;

    public static final String ROUTE_ID_ADMIN_DELETE_IMPORT_CONFIGURATION_SCHEDULER = "admin-delete-import-configuration-scheduler";
    public static final String ROUTE_ADMIN_DELETE_IMPORT_CONFIGURATION_SCHEDULER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_DELETE_IMPORT_CONFIGURATION_SCHEDULER;

    public static final String ROUTE_ID_ADMIN_VALIDATION_EXPORT_SCHEDULER = "admin-validation-export-scheduler";
    public static final String ROUTE_ADMIN_VALIDATION_EXPORT_SCHEDULER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_VALIDATION_EXPORT_SCHEDULER;

    public static final String ROUTE_ID_ADMIN_GET_CRON_VALIDATION_EXPORT_SCHEDULER = "admin-get-cron-validation-export-scheduler";
    public static final String ROUTE_ADMIN_GET_CRON_VALIDATION_EXPORT_SCHEDULER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_GET_CRON_VALIDATION_EXPORT_SCHEDULER;

    public static final String ROUTE_ID_ADMIN_DELETE_VALIDATION_EXPORT_SCHEDULER = "admin-delete-validation-export-scheduler";
    public static final String ROUTE_ADMIN_DELETE_VALIDATION_EXPORT_SCHEDULER = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_DELETE_VALIDATION_EXPORT_SCHEDULER;

    // map admin

    public static final String ROUTE_ID_ADMIN_FETCH_OSM = "admin-fetch-osm";
    public static final String ROUTE_ADMIN_FETCH_OSM = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_FETCH_OSM;

    public static final String ROUTE_ID_ADMIN_UPDATE_MAPBOX = "admin-update-mapbox";
    public static final String ROUTE_ADMIN_UPDATE_MAPBOX = DIRECT_ROUTE_PREFIX + ROUTE_ID_ADMIN_UPDATE_MAPBOX;

    // timetable admin
    public static final String ROUTE_ID_UPLOAD_FILES_AND_START_IMPORT = "uploadFilesAndStartImport";
    public static final String ROUTE_UPLOAD_FILES_AND_START_IMPORT = DIRECT_ROUTE_PREFIX + ROUTE_ID_UPLOAD_FILES_AND_START_IMPORT;

    public static final String ROUTE_ID_IMPORT_LAUNCH = "importLaunch";
    public static final String ROUTE_IMPORT_LAUNCH = DIRECT_ROUTE_PREFIX + ROUTE_ID_IMPORT_LAUNCH;

    public static final String ROUTE_ID_PROCESS_FILE_QUEUE = "ProcessFileQueue";
    public static final String ROUTE_PROCESS_FILE_QUEUE = DIRECT_JMS_ROUTE_PREFIX + ROUTE_ID_PROCESS_FILE_QUEUE;

    public static final String ROUTE_ID_CHOUETTE_EXPORT_NETEX_QUEUE = "ChouetteExportNetexQueue";
    public static final String ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE = DIRECT_JMS_ROUTE_PREFIX + ROUTE_ID_CHOUETTE_EXPORT_NETEX_QUEUE;

    public static final String ROUTE_ID_CHECK_SCHEDULED_JOBS_BEFORE_TRIGGERING_NEXT_ACTION = "checkScheduledJobsBeforeTriggeringNextAction";
    public static final String ROUTE_CHECK_SCHEDULED_JOBS_BEFORE_TRIGGERING_NEXT_ACTION = DIRECT_ROUTE_PREFIX + ROUTE_ID_CHECK_SCHEDULED_JOBS_BEFORE_TRIGGERING_NEXT_ACTION;

    public static final String ROUTE_ID_CHOUETTE_CLEAN_REFERENTIAL = "chouetteCleanReferential";
    public static final String ROUTE_CHOUETTE_CLEAN_REFERENTIAL = DIRECT_ROUTE_PREFIX + ROUTE_ID_CHOUETTE_CLEAN_REFERENTIAL;

    public static final String ROUTE_ID_UPDATE_STATUS = "updateStatus";
    public static final String ROUTE_UPDATE_STATUS = DIRECT_ROUTE_PREFIX + ROUTE_ID_UPDATE_STATUS;

    public static final String ROUTE_ID_PROCESS_EXPORT_RESULT = "processExportResult";
    public static final String ROUTE_PROCESS_EXPORT_RESULT = DIRECT_ROUTE_PREFIX + ROUTE_ID_PROCESS_EXPORT_RESULT;

    public static final String ROUTE_ID_ANALYS_RUNNING = "analysisRunning";
    public static final String ROUTE_ANALYS_RUNNING = DIRECT_ROUTE_PREFIX + ROUTE_ID_ANALYS_RUNNING;

    public static final String ROUTE_ID_ANALYSIS_OK = "analysisOk";
    public static final String ROUTE_ANALYSIS_OK = DIRECT_ROUTE_PREFIX + ROUTE_ID_ANALYSIS_OK;

    public static final String ROUTE_ID_ANALYSIS_ERROR = "analysisError";
    public static final String ROUTE_ANALYSIS_ERROR = DIRECT_ROUTE_PREFIX + ROUTE_ID_ANALYSIS_ERROR;

    public static final String ROUTE_ID_HANDLE_VALIDATION_SUCCESS = "handleValidationSuccess";
    public static final String ROUTE_HANDLE_VALIDATION_SUCCESS = DIRECT_ROUTE_PREFIX + ROUTE_ID_HANDLE_VALIDATION_SUCCESS;

    public static final String ROUTE_ID_VALIDATION_FAILED = "validationFailed";
    public static final String ROUTE_VALIDATION_FAILED = DIRECT_ROUTE_PREFIX + ROUTE_ID_VALIDATION_FAILED;

    public static final String ROUTE_ID_VALIDATION_GENERAL_ERROR = "validationGeneralError";
    public static final String ROUTE_VALIDATION_GENERAL_ERROR = DIRECT_ROUTE_PREFIX + ROUTE_ID_VALIDATION_GENERAL_ERROR;


}
