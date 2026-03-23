package no.rutebanken.marduk.services;

import jakarta.annotation.Nullable;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.ExportType;
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
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.*;

@Service
public class FaresScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(FaresScheduleService.class);

    // fields minutes, hours and day of weeks must be set, others must not be set
    private static final Pattern VALID_CRON_PATTERN = Pattern.compile("^0 \\d{1,2} \\d{1,2} \\? \\* [1-7](," +
            "[1-7])* \\*$");

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

    public void schedulePredefinedFaresExportForProvider(Long providerId, Long exportConfigurationId, String cronExpression) throws SchedulerException {
        if (!VALID_CRON_PATTERN.matcher(cronExpression).matches()) {
            throw new IllegalArgumentException("Invalid CRON expression " + cronExpression + ", must match pattern " + VALID_CRON_PATTERN.pattern());
        }

        Provider provider = providerRepository.getProvider(providerId);

        logger.info("Scheduling predefined FARES export for provider {} / exportConfigurationId {} with CRON expression {}", provider.getName(), exportConfigurationId, cronExpression);

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
                        .startAt(new Date())
                        .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression).withMisfireHandlingInstructionDoNothing())
                        .withIdentity(QuartzService.getPredefinedNetexFaresExportJobTriggerName(provider, exportConfigurationId))
                        .build();

        quartzService.rescheduleJob(job, trigger);
    }

    public @Nullable String getPredefinedFaresExportCronExpressionByProviderByExportConfigurationId(Long providerId, Long exportConfigurationId) {
        Provider provider = providerRepository.getProvider(providerId);

        return quartzService.findTriggerByName(QuartzService.getPredefinedNetexFaresExportJobTriggerName(provider, exportConfigurationId))
                .filter(CronTrigger.class::isInstance)
                .map(trigger -> ((CronTrigger) trigger).getCronExpression())
                .orElse(null);
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
                    .filter(exportTemplate -> ExportType.NETEX_FARES == exportTemplate.getType())
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
