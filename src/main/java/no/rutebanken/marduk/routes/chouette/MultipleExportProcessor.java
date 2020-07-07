package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ExportTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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
        exports.stream().forEach(e -> log.info("Multiple export : export " + e.getId() + "/" + e.getName()));

    }
}
