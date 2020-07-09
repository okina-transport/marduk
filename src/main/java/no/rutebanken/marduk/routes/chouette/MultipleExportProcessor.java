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

    @Autowired
    ProducerTemplate producer;

    @Override
    public void process(Exchange exchange) throws Exception {
        List<ExportTemplate> exports = (List<ExportTemplate>) exchange.getIn().getBody();
        exchange.getIn().setBody(null);
        exports.stream().forEach(export -> {
            log.info("Multiple export : export => " + export.getId() + "/" + export.getName());
            if (ExportType.NETEX.equals(export.getType())) {
                log.info("Routing to NETEX export => " + export.getId() + "/" + export.getName());
                exchange.getOut().setBody("Export id : " + export.getId());
                Map<String, Object> headers = exchange.getIn().getHeaders();
                headers.put(PROVIDER_ID, headers.get("providerId"));
                headers.put(NO_GTFS_EXPORT, constant(true));
                headers.put(Constants.FILE_NAME, "export-" + export.getId() + "-" + export.getName());
//                headers.put(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNetexExportResult"));
//                headers.put(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT_NETEX.name()));
//                headers.put(CHOUETTE_JOB_STATUS_URL, exchange.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
//                headers.put(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(exchange.getIn().getHeader("Location", String.class)));
                exchange.getOut().setHeaders(headers);
                producer.send("activemq:queue:ChouetteExportNetexQueue", exchange);
            } else {
                log.info("Routing not supported yet for => " + export.getId() + "/" + export.getName() + "/" + export.getType());
            }
        });
    }
}
