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

package no.rutebanken.marduk;

public class Constants {
    public static final String FILE_TYPE = "RutebankenFileType";
    public static final String FILE_HANDLE = "RutebankenFileHandle";
    public static final String EXPORT_FILE_NAME = "ExportFileName";
    public static final String ARCHIVE_FILE_HANDLE = "ArchiveFileHandle";
    public static final String FILE_PARENT_COLLECTION = "RutebankenFileParentCollection";
    public static final String PROVIDER_ID = "RutebankenProviderId";
    public static final String TOTALCOUNTPROVIDERS = "RutebankenAllCountProviders";
    public static final String COUNTPROVIDERS = "RutebankenCountProviders";
    public static final String PROVIDER_IDS = "RutebankenProviderIds";
    public static final String ORIGINAL_PROVIDER_ID = "RutebankenOriginalProviderId"; // The original provider id that started this chain of events
    public static final String EXPORT_LINES_IDS = "EXPORT_LINES_IDS";
    public static final String EXPORT_REFERENTIALS_NAMES = "EXPORT_REFERENTIALS_NAMES";
    public static final String EXPORT_START_DATE = "EXPORT_START_DATE";
    public static final String EXPORT_END_DATE = "EXPORT_END_DATE";
    public static final String EXPORT_NAME = "EXPORT_NAME";
    public static final String EXPORTED_FILENAME = "EXPORTED_FILENAME";
    public static final String CURRENT_EXPORT = "CURRENT_EXPORT";
    public static final String JSON_EXPORTS = "JSON_EXPORTS";
    public static final String STOP_ID_PREFIX = "STOP_ID_PREFIX";
    public static final String LINE_ID_PREFIX = "LINE_ID_PREFIX";
    public static final String ID_FORMAT = "ID_FORMAT";
    public static final String IMPORT_TYPE = "ImportType";
    public static final String SPLIT_CHARACTER = "splitCharacter";
    public static final String ROUTE_MERGE = "routeMerge";
    public static final String CLEAN_MODE = "cleanMode";
    public static final String IGNORE_COMMERCIAL_POINTS = "ignoreCommercialPoints";
    public static final String ANALYSIS_JOB_ID = "analysisJobId";
    public static final String KEEP_BOARDING_ALIGHTING_POSSIBILITY = "keepBoardingAlightingPossibility";
    public static final String KEEP_STOP_GEOLOCALISATION = "keepStopGeolocalisation";
    public static final String KEEP_STOP_NAMES = "keepStopNames";


    public static final String ID_SUFFIX = "ID_SUFFIX";
    public static final String COMMERCIAL_POINT_ID_PREFIX = "COMMERCIAL_POINT_ID_PREFIX";
    public static final String COMMERCIAL_POINT_EXPORT = "COMMERCIAL_POINT_EXPORT";
    public static final String STOP_AREA_PREFIX_TO_REMOVE = "stopAreaPrefixToRemove";
    public static final String AREA_CENTROID_PREFIX_TO_REMOVE = "areaCentroidPrefixToRemove";
    public static final String MAPPING_LINES_IDS = "MAPPING_LINES_IDS";
    public static final String KEEP_ORIGINAL_ID = "KEEP_ORIGINAL_ID";
    public static final String COMMERCIAL_POINT_ID_PREFIX_TO_REMOVE = "commercialPointIdPrefixToRemove";
    public static final String QUAY_ID_PREFIX_TO_REMOVE = "quayIdPrefixToRemove";
    public static final String LINE_PREFIX_TO_REMOVE = "linePrefixToRemove";

    public static final String IS_SIMULATION_EXPORT = "isSimulationExport";
    public static final String ROLE_EXPORT_SIMULATION = "exportSimulation";
    public static final String EXPORT_SIMULATION_NAME = "exportSimulationName";


    // tag for tiamat exports
    public static final String TIAMAT_STOP_PLACES_EXPORT = "TIAMAT_STOP_PLACES_EXPORT";
    public static final String TIAMAT_POINTS_OF_INTEREST_EXPORT = "TIAMAT_POINTS_OF_INTEREST_EXPORT";
    public static final String TIAMAT_PARKINGS_EXPORT = "TIAMAT_PARKINGS_EXPORT";

    // (providerId can change during the process when transferring data from one referential to another
    public static final String CORRELATION_ID = "RutebankenCorrelationId";
    public static final String CHOUETTE_REFERENTIAL = "RutebankenChouetteReferential";
    public static final String JSON_PART = "RutebankenJsonPart";
    public static final String OTP_GRAPH_DIR = "RutebankenOtpGraphDirectory";
    public static final String FILE_NAME = "RutebankenFileName";
    public static final String USER = "RutebankenUser";
    public static final String DESCRIPTION = "RutebankenDescription";
    public static final String NO_GTFS_EXPORT = "RutebankenNoGtfsExport";
    public static final String NETEX_EXPORT_GLOBAL = "RutebankenNetexExportGlobal";
    public static final String GTFS_EXPORT_GLOBAL = "RutebankenGtfsExportGlobal";
    public static final String IMPORT = "RutebankenImport";

    public static final String CURRENT_AGGREGATED_GTFS_FILENAME = "aggregated-gtfs.zip";
    public static final String CURRENT_AGGREGATED_NETEX_FILENAME = "aggregated-netex.zip";
    public static final String CURRENT_AGGREGATED_CONCERTO_FILENAME = "concerto.csv";
    public static final String CURRENT_AGGREGATED_NEPTUNE_FILENAME = "aggregated-neptune.zip";
    public static final String GRAPH_OBJ = "Graph.obj";
    public static final String BASE_GRAPH_OBJ = "baseGraph.obj";

    public static final String METADATA_DESCRIPTION = "MetadataDescription";
    public static final String METADATA_FILE = "MetadataFile";

    public static final String FILE_TARGET_MD5 = "RutebankenMd5SumRecordedForTargetFile";
    public static final String ENABLE_VALIDATION = "RutebankenEnableValidation";
    public static final String FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES = "RutebankenSkipStatusUpdateForDuplicateFiles";

    public static final String BLOBSTORE_PATH_INBOUND = "inbound/received/";
    public static final String BLOBSTORE_PATH_OUTBOUND = "outbound/";

    public static final String CHOUETTE_JOB_STATUS_URL = "RutebankenChouetteJobStatusURL";
    public static final String CHOUETTE_JOB_ID = "RutebankenChouetteJobId";
    public static final String CHOUETTE_JOB_STATUS_ROUTING_DESTINATION = "RutebankenChouetteJobStatusRoutingDestination";
    public static final String CHOUETTE_JOB_STATUS_JOB_TYPE = "RutebankenChouetteJobStatusType";
    public static final String CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL = "RutebankenChouetteJobStatusValidationLevel";

    public static final String JOB_ACTION = "RutebankenJobAction";

    public static final String BLOBSTORE_MAKE_BLOB_PUBLIC = "RutebankenBlobstoreMakeBlobPublic";

    public static final String SINGLETON_ROUTE_DEFINITION_GROUP_NAME = "RutebankenSingletonRouteDefinitionGroup";

    public static final String FOLDER_NAME = "RutebankenFolderName";
    public static final String SYSTEM_STATUS = "RutebankenSystemStatus";

    public static final String TIMESTAMP = "RutebankenTimeStamp";

    public static final String ETCD_KEY = "RutebankenEtcdKey";

    public static final String ET_CLIENT_NAME_HEADER = "ET-Client-Name";
    public static final String ET_CLIENT_ID_HEADER = "ET-Client-ID";

    public static final String PROVIDER_BLACK_LIST = "RutebankenProviderBlackList";
    public static final String PROVIDER_WHITE_LIST = "RutebankenProviderWhiteList";
    public static final String TRANSFORMATION_ROUTING_DESTINATION = "RutebankenTransformationRoutingDestination";

    public static final String OTP_BASE_GRAPH_BUILD = "RutebankenOtpBaseGraphBuild";

    public static final String TIME_ZONE = "Europe/Paris";

    public static final String OKINA_REFERENTIAL = "x-okina-referential";

    public static final String NOTIFICATION = "Notification";
    public static final String NOTIFICATION_URL = "NotificationUrl";

    public static final String IMPORT_CONFIGURATION_SCHEDULER = "importConfigurationScheduler";

    public static final String ANALYZE_ACTION = "Analyze";

    public static final String IMPORT_MODE = "ImportMode";

    public static final String MERGED_NETEX_ROOT_DIR = "mobiiti_technique/netex/merged";
    public static final String MERGED_NETEX_STOPS_ROOT_DIR = "mobiiti_technique/netex_stops";
    public static final String MERGED_NETEX_POI_ROOT_DIR = "mobiiti_technique/netex_poi";
    public static final String MERGED_NETEX_PARKINGS_ROOT_DIR = "mobiiti_technique/netex_parkings";
    public static final String MISSING_EXPORTS = "missingExports";
    public static final String IS_MERGED_NETEX_FAILED = "isMergedNetexFailed";

    public static final String IMPORT_CONFIGURATION_ID = "importConfigurationId";
    public static final String WORKLOW = "worklow";
    public static final String RECIPIENTS = "recipients";
    public static final String MULTIPLE_EXPORT = "multipleExport";


}

