package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import org.apache.camel.ProducerTemplate;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ConcertoJob implements Job {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    ProducerTemplate producer;

    @Override
    public void execute(JobExecutionContext context) {

        logger.info("Job export Concerto");

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, "1");
        headers.put(Constants.USER, "Automatisation");

        producer.sendBodyAndHeaders("activemq:queue:ChouetteExportConcertoQueue", null, headers);
    }
}