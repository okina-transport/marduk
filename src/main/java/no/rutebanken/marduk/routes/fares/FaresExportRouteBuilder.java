package no.rutebanken.marduk.routes.fares;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.ExportToConsumersProcessor;
import no.rutebanken.marduk.routes.chouette.UpdateExportTemplateProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.support.builder.PredicateBuilder.and;

@Component
public class FaresExportRouteBuilder extends BaseRouteBuilder {

    private final FaresExportProcessor faresExportProcessor;
    private final ExportToConsumersProcessor exportToConsumersProcessor;
    private final UpdateExportTemplateProcessor updateExportTemplateProcessor;

    public FaresExportRouteBuilder(FaresExportProcessor faresExportProcessor,
                                   ExportToConsumersProcessor exportToConsumersProcessor,
                                   UpdateExportTemplateProcessor updateExportTemplateProcessor) {
        this.faresExportProcessor = faresExportProcessor;
        this.exportToConsumersProcessor = exportToConsumersProcessor;
        this.updateExportTemplateProcessor = updateExportTemplateProcessor;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("jms:queue:NetexFaresPredefinedExport")
                .routeId("NetexFaresPredefinedExport")
                .streamCache(Boolean.TRUE)
                .transacted()
                .process(e -> e.getIn().setHeader(CORRELATION_ID,  UUID.randomUUID().toString()))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_FARES).state(JobEvent.State.PENDING).type("fares").build())
                .to("direct:updateStatus")
                .to("jms:queue:exportNetexFaresQueue")
                .end();

        singletonFrom("jms:queue:exportNetexFaresCompleted")
                .routeId("exportNetexFaresCompleted")
                .streamCache(Boolean.TRUE)
                .transacted()
                .log(LoggingLevel.INFO, correlation() + "Fares export completed")
                .process(faresExportProcessor)
                .to("direct:updateStatus")
                .choice()
                    .when(and(header(CURRENT_EXPORT).isNotNull(), header(FARES_EXPORT_STATUS).isEqualTo("OK")))
                        .process(exportToConsumersProcessor)
                        .to("direct:updateExportToConsumerStatus")
                        .process(updateExportTemplateProcessor)
                .end();
    }

}
