package no.rutebanken.marduk.routes.chouette;

import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Component
public class MultipleExportsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    private MultipleExportProcessor multipleExportProcessor;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:multipleExports").streamCaching()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting multiple exports route for provider with id ${header." + PROVIDER_ID + "}")
                .process(multipleExportProcessor)
                .routeId("marduk-multiple-exports-job");
    }
}
