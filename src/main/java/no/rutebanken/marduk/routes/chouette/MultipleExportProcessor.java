package no.rutebanken.marduk.routes.chouette;

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

import static no.rutebanken.marduk.Constants.NO_GTFS_EXPORT;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

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
        exports.stream().forEach(e -> {
            log.info("Multiple export : export => " + e.getId() + "/" + e.getName());
            if (ExportType.NETEX.equals(e.getType())) {
                log.info("Routing to NETEX export => " + e.getId() + "/" + e.getName());
                exchange.getOut().setBody("Export id : " + e.getId());
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                producer.send("activemq:queue:ChouetteExportNetexQueue", exchange);
            } else {
                log.info("Routing not supported yet for => " + e.getId() + "/" + e.getName() + "/" + e.getType());
            }
        });
    }
}
