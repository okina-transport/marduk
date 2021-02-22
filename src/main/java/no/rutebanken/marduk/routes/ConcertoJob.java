package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Consumer;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.repository.ConsumerDAO;
import no.rutebanken.marduk.routes.chouette.ExportJsonMapper;
import org.apache.camel.ProducerTemplate;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConcertoJob implements Job {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    ProducerTemplate producer;

    @Autowired
    ConsumerDAO consumerDAO;

    @Autowired
    ExportJsonMapper exportJsonMapper;

    @Override
    public void execute(JobExecutionContext context) {

        logger.info("Job export Concerto");

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, "1");
        headers.put(Constants.USER, "Mosaic");

        try {
            addConsumersConcerto(headers);
        } catch (Exception e) {
            logger.error("Error while processing export Concerto");
        }

        producer.sendBodyAndHeaders("activemq:queue:ChouetteExportConcertoQueue", null, headers);
    }

    private void addConsumersConcerto(Map<String, Object> headers) throws Exception {
        List<Consumer> consumerList = consumerDAO.getAllConsumersConcertoFromAdmin("admin");
        ExportTemplate export = new ExportTemplate();
        export.setId(1L);
        export.setName("Concerto Export");
        export.setType(ExportType.CONCERTO);
        export.setConsumers(consumerList);

        headers.put(Constants.CURRENT_EXPORT, exportJsonMapper.toJson(export));
    }
}