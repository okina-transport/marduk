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

package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class NetexImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

    static class Parameters {
        @JsonProperty("netexprofile-import")
        public Netex netexImport;
    }

    static class Netex extends AbstractImportParameters {
        @JsonProperty("parse_site_frames")
        private boolean parseSiteFrames = true;

        @JsonProperty("validate_against_schema")
        private boolean validateAgainstSchema = true;

        @JsonProperty("validate_against_profile")
        private boolean validateAgainstProfile = true;

        @JsonProperty("continue_on_line_errors")
        private boolean continueOnLineErrors = true;

        @JsonProperty("clean_on_error")
        private boolean cleanOnErrors = true;

        @JsonProperty("object_id_prefix")
        private String objectIdPrefix;

        @JsonProperty("import_mode")
        private ImportMode importMode = ImportMode.LINE;

        @JsonProperty("netex_import_layouts")
        private boolean netexImportLayouts = false;

        @JsonProperty("netex_import_colors")
        private boolean netexImportColors = false;

        @JsonProperty("use_target_network")
        private boolean useTargetNetwork = false;

        @JsonProperty("target_network")
        private String targetNetwork;
    }

    public static NetexImportParameters create(RawImportParameters rawImportParameters) {
        Netex netexImport = new Netex();
        Provider provider = rawImportParameters.getProvider();
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        netexImport.name = rawImportParameters.getFileName();
        netexImport.referentialName = provider.name;
        netexImport.organisationName = chouetteInfo.organisation;
        netexImport.userName = rawImportParameters.getUser();
        netexImport.cleanMode = rawImportParameters.getCleanMode();
        netexImport.stopAreaRemoteIdMapping = chouetteInfo.enableStopPlaceIdMapping;
        netexImport.objectIdPrefix = chouetteInfo.xmlns;
        netexImport.generateMissingRouteSectionsForModes = chouetteInfo.generateMissingServiceLinksForModes;
        netexImport.importMode = rawImportParameters.getImportMode();
        netexImport.keepBoardingAlighting = rawImportParameters.isKeepBoardingAlighting();
        netexImport.keepStopGeolocalisation = rawImportParameters.isKeepStopGeolocalisation();
        netexImport.keepStopNames = rawImportParameters.isKeepStopNames();
        netexImport.generateMapMatching = rawImportParameters.isGenerateMapMatching();
        netexImport.description = rawImportParameters.getDescription();
        netexImport.netexImportLayouts = rawImportParameters.isNetexImportLayouts();
        netexImport.netexImportColors = rawImportParameters.isNetexImportColors();
        if (chouetteInfo.allowCreateMissingStopPlace) {
            netexImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.CREATE_NEW;
        }
        netexImport.useTargetNetwork = rawImportParameters.isUseTargetNetwork();
        netexImport.targetNetwork = rawImportParameters.getTargetNetwork();
        Parameters parameters = new Parameters();
        parameters.netexImport = netexImport;
        NetexImportParameters netexImportParameters = new NetexImportParameters();
        netexImportParameters.parameters = parameters;
        netexImportParameters.enableValidation = chouetteInfo.enableValidation;
        return netexImportParameters;
    }

}
