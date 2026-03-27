package no.rutebanken.marduk.routes.uttu;

import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class UttuImportProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange.getIn().getHeader(ALLOW_GTFS_FLEX).equals(false)) {
            return;
        }
        JobEvent.Builder jobEventBuilder =
                JobEvent.providerJobBuilder(exchange).timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.PENDING).type(String.valueOf(FileType.GTFS_FLEX));
        if ("OK".equals(exchange.getIn().getHeader(UTTU_IMPORT_STATUS))) {
            jobEventBuilder.state(JobEvent.State.OK);
        } else {
            jobEventBuilder.state(JobEvent.State.FAILED);
        }
//        exchange.getIn().setHeader(EXPORT_FROM_FARES, Boolean.TRUE);
        jobEventBuilder.build();
    }

}
