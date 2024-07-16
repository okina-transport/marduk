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

package no.rutebanken.marduk.routes.chouette.json.exporter;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.AttributionsExportModes;
import no.rutebanken.marduk.domain.IdFormat;
import no.rutebanken.marduk.routes.chouette.json.IdParameters;

import java.util.Date;

public class GtfsExportParameters {

    public Parameters parameters;

    public GtfsExportParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("gtfs-export")
        public GtfsExport gtfsExport;

        public Parameters(GtfsExport gtfsExport) {
            this.gtfsExport = gtfsExport;
        }

    }

    public static class GtfsExport extends AbstractExportParameters {

        @JsonProperty("object_id_prefix")
        public String objectIdPrefix;

        @JsonProperty("references_type")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String referencesType = "";

        @JsonProperty("time_zone")
        public String timeZone = Constants.TIME_ZONE;

        @JsonProperty("route_type_id_scheme")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String routeTypeIdScheme = "extended";

        @JsonProperty("keep_original_id")
        public boolean keepOriginalId = true;

        @JsonProperty("use_extended_gtfs_route_types")
        public boolean useExtendedGtfsRouteTypes = false;

        @JsonProperty("exported_filename")
        public String exportedFilename;

        @JsonProperty("stop_id_prefix")
        public String stopIdPrefix;

        @JsonProperty("line_id_prefix")
        public String lineIdPrefix;

        @JsonProperty("id_format")
        public IdFormat idFormat;

        @JsonProperty("id_suffix")
        public String idSuffix;

        @JsonProperty("commercial_point_id_prefix")
        public String commercialPointIdPrefix;

        @JsonProperty("commercial_point_export")
        private Boolean commercialPointExport;

        @JsonProperty("google_maps_compatibility")
        private Boolean googleMapsCompatibility;

        @JsonProperty("mapping_lines_ids")
        public boolean mappingLinesIds = false;

        @JsonProperty("attributions_export_mode")
        public AttributionsExportModes attributionsExportModes = AttributionsExportModes.NONE;

        public GtfsExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, boolean keepOriginalId, Date startDate, Date endDate, String exportedFilename, IdParameters idParams, boolean mappingLinesIds, Boolean commercialPointExport, AttributionsExportModes attributionsExportModes, Boolean googleMapsCompatibility, Boolean useExtendedGtfsRouteTypes) {
            this.name = name;
            this.objectIdPrefix = objectIdPrefix;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.startDate = (startDate != null) ? startDate : DateUtils.startDateFor(2L);
            this.endDate = (endDate != null) ? endDate : DateUtils.endDateFor(365);
            this.keepOriginalId = keepOriginalId;
            this.exportedFilename = exportedFilename;
            this.stopIdPrefix = idParams.getStopIdPrefix();
            this.idFormat = idParams.getIdFormat();
            this.idSuffix = idParams.getIdSuffix();
            this.lineIdPrefix = idParams.getLineIdPrefix();
            this.commercialPointIdPrefix = idParams.getCommercialPointIdPrefix();
            this.commercialPointExport = commercialPointExport;
            this.googleMapsCompatibility = googleMapsCompatibility;
            this.mappingLinesIds = mappingLinesIds;
            this.attributionsExportModes = attributionsExportModes;
            this.useExtendedGtfsRouteTypes = useExtendedGtfsRouteTypes;
        }

        public GtfsExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, boolean keepOriginalId, String exportedFilename) {
            this(name, objectIdPrefix, referentialName, organisationName, userName, keepOriginalId, null, null, exportedFilename, new IdParameters(), false, false, AttributionsExportModes.NONE, false, false);
        }

    }


}
