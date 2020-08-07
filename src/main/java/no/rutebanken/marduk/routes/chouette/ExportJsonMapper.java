package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.ExportTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ExportJsonMapper {
    private static ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES,true);
    }

    public String toJson(ExportTemplate export) throws Exception {
        return mapper.writeValueAsString(export);
    }

    public ExportTemplate fromJson(String json) throws IOException {
        return mapper.readValue(json, new TypeReference<ExportTemplate>() { });
    }
}
