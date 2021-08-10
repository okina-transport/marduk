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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;
import no.rutebanken.marduk.routes.chouette.json.IdParameters;

import java.util.Set;

public class NeptuneImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

    static class Parameters {

        @JsonProperty("neptune-import")
        public Neptune neptuneImport;

    }

    static class Neptune extends AbstractImportParameters {

        @JsonProperty("stopArea_prefix_to_remove")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String stopAreaPrefixToRemove = "";

        @JsonProperty("areaCentroid_prefix_to_remove")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String areaCentroidPrefixToRemove = "";

        @JsonProperty("line_prefix_to_remove")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String linePrefixToRemove = "";

        @JsonProperty("ignore_commercial_points")
        public boolean ignoreCommercialPoints = true;


    }

    public static NeptuneImportParameters create(String name, String referentialName, String organisationName,
                                                 String userName, boolean cleanRepository, boolean enableValidation,
                                                 boolean allowCreateMissingStopPlace, boolean enableStopPlaceIdMapping,
                                                 Set<String> generateMissingRouteSectionsForModes, String description,
                                                 IdParameters idParameters, boolean ignoreCommercialPoints, boolean isAnalyzeJob) {
        Neptune neptuneImport = new Neptune();
        neptuneImport.name = name;
        neptuneImport.referentialName = referentialName;
        neptuneImport.organisationName = organisationName;
        neptuneImport.userName = userName;
        neptuneImport.description = description;
        neptuneImport.cleanRepository = cleanRepository ? "1" : "0";
        neptuneImport.stopAreaRemoteIdMapping = enableStopPlaceIdMapping;
        neptuneImport.generateMissingRouteSectionsForModes = generateMissingRouteSectionsForModes;
        if (allowCreateMissingStopPlace && !isAnalyzeJob) {
            neptuneImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.CREATE_NEW;
        } else {
            neptuneImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.READ_ONLY;
        }
        neptuneImport.areaCentroidPrefixToRemove = idParameters.getAreaCentroidPrefixToRemove();
        neptuneImport.stopAreaPrefixToRemove = idParameters.getStopAreaPrefixToRemove();
        neptuneImport.linePrefixToRemove = idParameters.getLinePrefixToRemove();
        neptuneImport.ignoreCommercialPoints = ignoreCommercialPoints;

        Parameters parameters = new Parameters();
        parameters.neptuneImport = neptuneImport;
        NeptuneImportParameters neptuneImportParameters = new NeptuneImportParameters();
        neptuneImportParameters.parameters = parameters;
        neptuneImportParameters.enableValidation = enableValidation;

        return neptuneImportParameters;
    }

}
