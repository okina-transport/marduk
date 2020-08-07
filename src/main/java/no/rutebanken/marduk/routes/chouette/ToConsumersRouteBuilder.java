package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ExportTemplate;
import org.apache.camel.LoggingLevel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Component
public class ToConsumersRouteBuilder extends AbstractChouetteRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:toConsumers").streamCaching()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting sending exports to consumers for provider with id ${header." + PROVIDER_ID + "}")
                .process(exchange -> {
                    List<ExportTemplate> exports = (List<ExportTemplate>) exchange.getIn().getBody();
                    exchange.getIn().setBody(null);
                    exports.stream().forEach(export -> {
                        export.getConsumers().forEach(c -> {
                            log.info("Consumer => " + c.getName() + " towards => " + c.getS3Url());
                        });

                    });
                })
                .routeId("send-exports-to-consumers-job");
    }


//    from("file://C:\\test")
//          .choice()
//          .when(simple("${in.header.CamelFileName} contains '*.xlsx'"))
//            .to("ftp://rob@10.171.16.100/home/rob/test/?password=rob")
//          .otherwise()
//          .to("log://org.apache.camel.howto?showAll=true&level=DEBUG");

}
