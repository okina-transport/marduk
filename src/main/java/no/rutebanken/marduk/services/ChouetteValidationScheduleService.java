package no.rutebanken.marduk.services;

import jakarta.annotation.Nullable;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.jobs.ChouetteValidationExportJob;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Service
public class ChouetteValidationScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ChouetteValidationScheduleService.class);

    private final ProviderRepository providerRepository;
    private final QuartzService quartzService;

    ChouetteValidationScheduleService(ProviderRepository providerRepository, QuartzService quartzService) {
        this.providerRepository = providerRepository;
        this.quartzService = quartzService;
    }

    public void scheduleManualValidationForProvider(Long providerId, Date when) throws SchedulerException {
        Provider provider = providerRepository.getProvider(providerId);

        logger.info("Scheduling manual validation & export for provider {} at {}", provider.getName(), when);

        JobDataMap jobDataMap = new JobDataMap(Map.of(PROVIDER_ID, providerId));

        JobDetail job =
                JobBuilder.newJob(ChouetteValidationExportJob.class)
                        .setJobData(jobDataMap)
                        .withIdentity(QuartzService.getManualChouetteValidationExportJobName(provider))
                        .build();

        Trigger trigger =
                TriggerBuilder
                        .newTrigger()
                        .startAt(when)
                        .withIdentity(QuartzService.getManualChouetteValidationExportJobTriggerName(provider))
                        .build();

        quartzService.rescheduleJob(job, trigger);
    }

    public @Nullable Date getNextManualValidationScheduledForProvider(Long providerId) {
        Provider provider = providerRepository.getProvider(providerId);

        // in case a user requested a VALIDATION trigger manually
        Optional<Date> nextManualValidationTrigger =
                quartzService.getNextFireTimeForTrigger(QuartzService.getManualChouetteValidationExportJobTriggerName(provider));

        return nextManualValidationTrigger.orElse(null);
    }

}
