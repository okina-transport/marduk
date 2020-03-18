package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class FileSystemRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:getStopPlacesFile")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("fileSystemService", "getLatestStopPlacesFile")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .routeId("file-stop-places-download");

        from("direct:getOfferFile")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("fileSystemService", "getOfferFile")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .routeId("file-offer-download");
    }

}
