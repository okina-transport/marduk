package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ExportTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CURRENT_EXPORT;

@Component
public class ExportToConsumersProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ExportJsonMapper exportJsonMapper;

    @Override
    public void process(Exchange e) throws Exception {
        String jsonExport = (String) e.getOut().getHeaders().get(CURRENT_EXPORT);
        ExportTemplate export = exportJsonMapper.fromJson(jsonExport);
        log.info("Found " + export.getConsumers().size() + " for export " + export.getId() + "/" + export.getName());
    }
}
