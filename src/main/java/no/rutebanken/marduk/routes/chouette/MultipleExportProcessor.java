package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.Utils.getLastPathElementOfUrl;
import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.Builder.header;

/**
 * Handles multiple exports
 */
@Component
public class MultipleExportProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    public static final String MOSAIC_REFERENTIAL = "MOSAIC_REFERENTIAL";

    @Autowired
    ProducerTemplate producer;

    @Override
    public void process(Exchange exchange) throws Exception {
        List<ExportTemplate> exports = (List<ExportTemplate>) exchange.getIn().getBody();
        exchange.getIn().setBody(null);
        exports.stream().forEach(export -> {
            log.info("Multiple export : export => " + export.getId() + "/" + export.getName());
            if (ExportType.NETEX.equals(export.getType())) {
                toNetexExport(export, exchange);
            } else if (ExportType.GTFS == export.getType()) {
                toGtfsExport(export, exchange);
            } else {
                log.info("Routing not supported yet for => " + export.getId() + "/" + export.getName() + "/" + export.getType());
            }
        });
    }


    private void toNetexExport(ExportTemplate export, Exchange exchange) {
        log.info("Routing to NETEX export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        producer.send("activemq:queue:ChouetteExportNetexQueue", exchange);
    }

    private void toGtfsExport(ExportTemplate export, Exchange exchange) {
        log.info("Routing to GTFS export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        producer.send("activemq:queue:ChouetteExportGtfsQueue", exchange);
    }


    private void prepareHeadersForExport(Exchange exchange, ExportTemplate export) {
        boolean noGtfs = export.getType() != ExportType.GTFS;
        exchange.getIn().getHeaders().put(NO_GTFS_EXPORT, noGtfs);
        exchange.getOut().setBody("Export id : " + export.getId());
        Map<String, Object> headers = exchange.getIn().getHeaders();
        headers.put(PROVIDER_ID, headers.get("providerId"));
        headers.put(NO_GTFS_EXPORT, noGtfs);
        headers.put(Constants.FILE_NAME, "export-" + export.getId() + "-" + export.getName());
        exchange.getOut().setHeaders(headers);
    }
}
