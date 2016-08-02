package no.rutebanken.marduk.routes.chouette.json;

import org.junit.Test;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;

public class ImportParametersTest {

    final String gtfsReferenceJson =  "{ \"parameters\": { \"gtfs-import\": {\"clean_repository\":\"0\",\"no_save\": \"0\", " +
            "\"user_name\": \"Chouette\", \"name\": \"test\", \"organisation_name\": \"Rutebanken\", \"referential_name\": " +
            "\"testDS\", \"object_id_prefix\": \"tds\", \"max_distance_for_commercial\": \"0\", " +
            "\"ignore_last_word\": \"0\", \"ignore_end_chars\": \"0\"," +
            "\"route_type_id_scheme\": \"any\"," +
            " \"max_distance_for_connection_link\": \"0\", \"references_type\": \"\" } } }";

    final String regtoppReferenceJson =  "{\"parameters\":{\"regtopp-import\":{\"name\":\"test\",\"clean_repository\":\"0\",\"no_save\":\"0\"," +
            "\"user_name\":\"Chouette\",\"organisation_name\":\"Rutebanken\",\"referential_name\":\"testDS\",\"object_id_prefix\":\"tds\"," +
           
            "\"references_type\":\"\",\"version\":\"R12\",\"coordinate_projection\":\"EPSG:32632\",\"calendar_strategy\":\"ADD\"}}}";

    @Test
    public void createGtfsImportParameters() throws Exception {
        GtfsImportParameters importParameters = GtfsImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette",false,false);
        assertJsonEquals(gtfsReferenceJson, importParameters.toJsonString());
    }

    @Test
    public void createRegtoppImportParameters() throws Exception {
        RegtoppImportParameters importParameters = RegtoppImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette", "R12", "EPSG:32632","ADD",false,false);
        System.out.println(importParameters.toJsonString());
        assertJsonEquals(regtoppReferenceJson, importParameters.toJsonString());
    }

    @Test
    public void createRegtoppImportParametersWithValidation() throws Exception {
        RegtoppImportParameters importParameters = RegtoppImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette", "R12", "EPSG:32632","ADD",false,true);
        System.out.println(importParameters.toJsonString());
      
    }

}