package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;

public class RegtoppImportParameters {

    public Parameters parameters;

    static class Parameters {

        @JsonProperty("regtopp-import")
        public RegtoppImport regtoppImport;

    }

    static class RegtoppImport {

        public String name;

        @JsonProperty("no_save")
        public String noSave = "0";

        @JsonProperty("user_name")
        public String userName;

        @JsonProperty("organisation_name")
        public String organisationName;

        @JsonProperty("referential_name")
        public String referentialName;

        @JsonProperty("object_id_prefix")
        public String objectIdPrefix;

        @JsonProperty("max_distance_for_commercial")
        public String maxDistanceForCommercial = "0";

        @JsonProperty("ignore_last_word")
        public String ignoreLastWord = "0";

        @JsonProperty("ignore_end_chars")
        public String ignoreEndChars = "0";

        @JsonProperty("max_distance_for_connection_link")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String maxDistanceForConnectionLink = "0";

        @JsonProperty("references_type")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String referencesType = "";

        @JsonProperty("version")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String version;

        @JsonProperty("coordinate_projection")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String coordinateProjection;

    }

    public static RegtoppImportParameters create(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, String version, String coordinateProjection) {
        RegtoppImport regtoppImport = new RegtoppImport();
        regtoppImport.name = name;
        regtoppImport.objectIdPrefix = objectIdPrefix;
        regtoppImport.referentialName = referentialName;
        regtoppImport.organisationName = organisationName;
        regtoppImport.userName = userName;
        regtoppImport.version = version; //R11D, R12, R12N, R13A
        regtoppImport.coordinateProjection = coordinateProjection; //EPSG:32632 (UTM32_N)
        Parameters parameters = new Parameters();
        parameters.regtoppImport = regtoppImport;
        RegtoppImportParameters regtoppImportParameters = new RegtoppImportParameters();
        regtoppImportParameters.parameters = parameters;
        return regtoppImportParameters;
    }


    public String toJsonString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}