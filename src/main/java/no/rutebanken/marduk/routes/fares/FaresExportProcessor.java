package no.rutebanken.marduk.routes.fares;

import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class FaresExportProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange.getIn().getHeader(CURRENT_EXPORT) == null) {
            return;
        }
        JobEvent.Builder jobEventBuilder =
                JobEvent.providerJobBuilder(exchange).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_FARES).state(JobEvent.State.PENDING).type("fares");
        if ("OK".equals(exchange.getIn().getHeader(FARES_EXPORT_STATUS))) {
            jobEventBuilder.state(JobEvent.State.OK);
        } else {
            jobEventBuilder.state(JobEvent.State.FAILED);
        }
        exchange.getIn().setHeader(EXPORT_FROM_FARES, Boolean.TRUE);
        jobEventBuilder.build();
    }

}
