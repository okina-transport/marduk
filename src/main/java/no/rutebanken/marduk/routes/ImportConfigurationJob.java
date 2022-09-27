package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
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
public class ImportConfigurationJob implements Job {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    ProducerTemplate producer;

    @Autowired
    ImportConfigurationDAO importConfigurationDAO;

    @Autowired
    ProviderRepository providerRepository;

    @Override
    public void execute(JobExecutionContext context) {

        logger.info("Job import configuration");

        Provider provider = providerRepository.findByName(context.getJobDetail().getKey().getName().split("-", 3)[1]);
        String importConfigurationId = context.getJobDetail().getKey().getName().split("-", 3)[2];

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.USER, "Mobi-iti");
        headers.put(Constants.PROVIDER_ID, provider.getId());
        headers.put(Constants.IMPORT_CONFIGURATION_ID, importConfigurationId);

        producer.sendBodyAndHeaders("activemq:queue:ImportConfigurationQueue", null, headers);
    }
}