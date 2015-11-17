package no.rutebanken.marduk.routes.avinor.enrichment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jacksonxml.JacksonXMLDataFormat;
import org.springframework.stereotype.Component;

/**
 * Downloads data from Avinor periodically. Currently OSL.
 * Example query: http://flydata.avinor.no/XmlFeed.asp?TimeFrom=1&TimeTo=7&airport=OSL&direction=D&lastUpdate=2009-03-10T15:03:00Z
 */
@Component
public class AvinorEnrichmentRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        JacksonXMLDataFormat format = new JacksonXMLDataFormat(AirlineName.class);
        format.useList();
        format.setInclude("NON_NULL");
        format.disableFeature(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

//        from("quartz2://backend/avinor?cron=0+0+0/24+1/1+*+?")
        from("quartz2://avinor/airlinenames?cron=0+0/2+*+*+*+?")
                .log("Starting avinor enrichment data import.")
                .to("http4://flydata.avinor.no/airlineNames.asp")
                .unmarshal(format)
                .bean(AirlineNameRepositoryImpl.class, "add");

    }

}
