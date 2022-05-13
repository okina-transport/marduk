package no.rutebanken.marduk.Utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

	@Override
	public void serialize(LocalDateTime value, JsonGenerator jgen,
						  SerializerProvider provider) throws IOException,
		JsonProcessingException {
		Timestamp tsp = Timestamp.valueOf(value);
		jgen.writeNumber(tsp.getTime());
	}

}
