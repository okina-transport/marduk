package no.rutebanken.marduk.jobs;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.ProducerTemplate;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;

@Component
public class FaresExportJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(FaresExportJob.class);

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    ProducerTemplate producer;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        Long providerId = jobDataMap.getLong(PROVIDER_ID);
        Provider provider = providerRepository.getProvider(providerId);

        logger.info("FaresExportJob triggered for provider {}", provider.getName());

        String user = jobDataMap.getString(USER);
        Map<String, Object> headers = new HashMap<>();
        headers.put(USER, user);
        headers.put(PROVIDER_ID, providerId);
        headers.put(CHOUETTE_REFERENTIAL, provider.getName());
        producer.sendBodyAndHeaders("jms:queue:exportNetexFaresQueue", null, headers);
    }
}