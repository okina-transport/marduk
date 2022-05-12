package no.rutebanken.marduk.Utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

	Logger logger = LoggerFactory.getLogger(getClass());


	@Override
	public LocalDateTime deserialize(JsonParser jp, DeserializationContext ctxt) {
		LocalDateTime ldt = null;
		try {
			String dateVal = jp.getValueAsString();
			Long tsp = Long.valueOf(dateVal);
			ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(tsp), ZoneId.systemDefault());
		} catch (IOException e) {
			logger.error("Error localDateTime deserializer : ", e);
		}

		return ldt;
	}


}