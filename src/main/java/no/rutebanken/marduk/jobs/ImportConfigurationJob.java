package no.rutebanken.marduk.jobs;

import no.rutebanken.marduk.domain.Provider;
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

import static no.rutebanken.marduk.Constants.*;

@Component
public class ImportConfigurationJob implements Job {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    ProducerTemplate producer;

    @Autowired
    ProviderRepository providerRepository;

    @Override
    public void execute(JobExecutionContext context) {
        Provider provider = providerRepository.findByName(context.getJobDetail().getKey().getName().split("-", 3)[1]);
        String importConfigurationId = context.getJobDetail().getKey().getName().split("-", 3)[2];

        logger.info("ImportConfigurationJob triggerd for provider {} and import configuration id {}", provider.getName(), importConfigurationId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(USER, "Mobi-iti");
        headers.put(PROVIDER_ID, provider.getId());
        headers.put(IMPORT_CONFIGURATION_ID, importConfigurationId);

        producer.sendBodyAndHeaders("jms:queue:ImportConfigurationQueue?jmsMessageType=Map", null, headers);
    }
}