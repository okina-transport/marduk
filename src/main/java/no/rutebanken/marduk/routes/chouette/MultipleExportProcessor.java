package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.ExportType;
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
import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.Builder.header;

/**
 * Handles multiple exports
 */
@Component
public class MultipleExportProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ProducerTemplate producer;

    @Override
    public void process(Exchange exchange) throws Exception {
        List<ExportTemplate> exports = (List<ExportTemplate>) exchange.getIn().getBody();
        exchange.getIn().setBody(null);
        exports.stream().forEach(e -> {
            log.info("Multiple export : export => " + e.getId() + "/" + e.getName());
            if (ExportType.NETEX.equals(e.getType())) {
                log.info("Routing to NETEX export => " + e.getId() + "/" + e.getName());
                exchange.getOut().setBody("Export id : " + e.getId());
                Map<String, Object> headers = exchange.getIn().getHeaders();
                headers.put(PROVIDER_ID, headers.get("providerId"));
                headers.put(NO_GTFS_EXPORT, constant(true));
                headers.put(Constants.FILE_NAME, "export-" + e.getId() + "-" + e.getName());
                exchange.getOut().setHeaders(headers);
                producer.send("activemq:queue:ChouetteExportNetexQueue", exchange);
            } else {
                log.info("Routing not supported yet for => " + e.getId() + "/" + e.getName() + "/" + e.getType());
            }
        });
    }
}
