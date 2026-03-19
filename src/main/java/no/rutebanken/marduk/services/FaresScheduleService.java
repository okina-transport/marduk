package no.rutebanken.marduk.services;

import jakarta.annotation.Nullable;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.jobs.FaresExportJob;
import no.rutebanken.marduk.jobs.FaresPredefinedExportJob;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

import static no.rutebanken.marduk.Constants.*;

@Service
public class FaresScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(FaresScheduleService.class);

    private final ProviderRepository providerRepository;
    private final QuartzService quartzService;
    private final ExportTemplateDAO exportTemplateDAO;

    public FaresScheduleService(ProviderRepository providerRepository, QuartzService quartzService, ExportTemplateDAO exportTemplateDAO) {
        this.providerRepository = providerRepository;
        this.quartzService = quartzService;
        this.exportTemplateDAO = exportTemplateDAO;
    }

    public void scheduleManualFaresExportForProvider(Long providerId, Date when, String user) throws SchedulerException {
        Provider provider = providerRepository.getProvider(providerId);

        logger.info("Scheduling manual FARES export for provider {} at {}", provider.getName(), when);

        JobDataMap jobDataMap = new JobDataMap(
                Map.of(
                        PROVIDER_ID, providerId,
                        USER, user
                )
        );

        JobDetail job =
                JobBuilder.newJob(FaresExportJob.class)
                        .setJobData(jobDataMap)
                        .withIdentity(QuartzService.getManualNetexFaresExportJobName(provider))
                        .build();

        Trigger trigger =
                TriggerBuilder
                        .newTrigger()
                        .startAt(when)
                        .withIdentity(QuartzService.getManualNetexFaresExportJobTriggerName(provider))
                        .build();

        quartzService.rescheduleJob(job, trigger);
    }

    public void schedulePredefinedFaresExportForProvider(Long providerId, Date when, Long exportConfigurationId) throws SchedulerException {
        Provider provider = providerRepository.getProvider(providerId);

        logger.info("Scheduling predefined FARES export for provider {} / exportConfigurationId {} at {}", provider.getName(), exportConfigurationId, when);

        JobDataMap jobDataMap = new JobDataMap(
                Map.of(
                        PROVIDER_ID, providerId,
                        EXPORT_CONFIGURATION_ID, exportConfigurationId
                )
        );

        JobDetail job =
                JobBuilder.newJob(FaresPredefinedExportJob.class)
                        .setJobData(jobDataMap)
                        .withIdentity(QuartzService.getPredefinedNetexFaresExportJobName(provider, exportConfigurationId))
                        .build();

        Trigger trigger =
                TriggerBuilder
                        .newTrigger()
                        .startAt(when)
                        .withIdentity(QuartzService.getPredefinedNetexFaresExportJobTriggerName(provider, exportConfigurationId))
                        .build();

        quartzService.rescheduleJob(job, trigger);
    }

    public void unschedulePredefinedFaresExportForProvider(Long providerId, Long exportConfigurationId) throws SchedulerException {
        Provider provider = providerRepository.getProvider(providerId);

        logger.info("Unschedule predefined FARES export for provider {} / exportConfigurationId {}", provider.getName(), exportConfigurationId);

        quartzService.deleteJobByName(QuartzService.getPredefinedNetexFaresExportJobName(provider, exportConfigurationId));
    }

    public @Nullable Date getNextScheduledFaresExportForProvider(Long providerId) {
        Provider provider = providerRepository.getProvider(providerId);

        List<Date> nextNetexFaresTriggerDates = new ArrayList<>();

        // Get the next trigger(s) of all predefined export job(s)
        // There may be no predefined export job(s) for this provider
        // and predefined export may have no trigger scheduled
        List<ExportTemplate> exportTemplates = null;
        try {
            exportTemplates = exportTemplateDAO.getAll(provider.getChouetteInfo().getReferential());
        } catch (Exception e) {
            logger.error("Error retrieving export templates for provider {}", provider.getName(), e);
        }
        if (CollectionUtils.isNotEmpty(exportTemplates)) {
            exportTemplates.stream()
                    .map(e -> QuartzService.getPredefinedNetexFaresExportJobTriggerName(provider, e.getId()))
                    .map(quartzService::getNextFireTimeForTrigger)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(nextNetexFaresTriggerDates::add);
        }

        // Get the next trigger of a manual export job if it is scheduled
        quartzService.getNextFireTimeForTrigger(QuartzService.getManualNetexFaresExportJobTriggerName(provider))
                .ifPresent(nextNetexFaresTriggerDates::add);

        if (CollectionUtils.isEmpty(nextNetexFaresTriggerDates)) {
            // No predefined export or manual export jobs are scheduled
            return null;
        }

        return Collections.min(nextNetexFaresTriggerDates);
    }

}
