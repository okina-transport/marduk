package no.rutebanken.marduk.jobs;

import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.domain.Recipient;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
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
public class ChouetteValidationExportJob implements Job {

    private final static Logger logger = LoggerFactory.getLogger(ChouetteValidationExportJob.class);

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ImportConfigurationDAO importConfigurationDAO;

    @Autowired
    ProducerTemplate producer;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        Long providerId = jobDataMap.getLong(PROVIDER_ID);
        Provider provider = providerRepository.getProvider(providerId);
        logger.info("ChouetteValidationExportJob triggered for provider {}", provider.getName());

        Map<String, Object> headers = new HashMap<>();
        headers.put(USER, "Mobi-iti");
        headers.put(PROVIDER_ID, providerId);
        headers.put(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
        headers.put(WORKLOW, "EXPORT");
        if (jobDataMap.containsKey(IMPORT_CONFIGURATION_ID)) {
            // retrieve recipients from import configuration
            String importConfigurationId = jobDataMap.getString(IMPORT_CONFIGURATION_ID);
            try {
                ImportConfiguration importConfiguration = importConfigurationDAO.getImportConfiguration(provider.getChouetteInfo().getReferential(), importConfigurationId);
                if (importConfiguration != null && CollectionUtils.isNotEmpty(importConfiguration.getRecipients())) {
                    StringBuilder recipients = new StringBuilder();
                    for (Recipient recipient : importConfiguration.getRecipients()) {
                        recipients.append(recipient.getEmail());
                        recipients.append(",");
                    }
                    headers.put(RECIPIENTS, recipients.toString());
                }
            } catch (Exception e) {
                logger.error("Error retrieving import configuration {}", importConfigurationId, e);
                logger.warn("Import recipients not set");
            }
        }

        producer.sendBodyAndHeaders("jms:queue:ChouetteTransferExportQueue", null, headers);
    }
}