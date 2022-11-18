package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.exporter.AbstractExportParameters;

public class MapMatchingParameters {

    /*
     *     "MapMatchingParameters": {
            "user_name": "Rutebanken admin",
            "referential_name": "Hedmark / Hedmark-Trafikk"
        },

     * */

    public MapMatchingParameters.Parameters parameters;

    public MapMatchingParameters(MapMatchingParameters.Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("mapmatching")
        public MapMatchingParameters.Mapmatching mapmatching;

        public Parameters(MapMatchingParameters.Mapmatching mapmatching) {
            this.mapmatching = mapmatching;
        }

    }

    public static class Mapmatching extends AbstractExportParameters {

        @JsonProperty("user_name")
        public String userName;

        @JsonProperty("referential_name")
        public String referentialName;


        public Mapmatching(String userName, String referentialName) {
            this.referentialName = referentialName;
            this.userName = userName;
        }

    }
}
