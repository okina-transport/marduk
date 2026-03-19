package no.rutebanken.marduk.services;

import no.rutebanken.marduk.domain.Provider;
import org.jspecify.annotations.NonNull;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class QuartzService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzService.class);

    private final SchedulerFactoryBean schedulerFactoryBean;

    public QuartzService(SchedulerFactoryBean schedulerFactoryBean) {
        this.schedulerFactoryBean = schedulerFactoryBean;
    }

    public static @NonNull String getImportConfigurationJobTriggerName(Provider provider, Integer importConfigurationId) {
        return "ImportConfigurationJobTrigger-" + provider.chouetteInfo.referential + "-" + importConfigurationId;
    }

    public static @NonNull String getImportConfigurationJobName(Provider provider, Integer importConfigurationId) {
        return "ImportConfigurationJobDetails-" + provider.chouetteInfo.referential + "-" + importConfigurationId;
    }

    public static @NonNull String getAutomaticChouetteValidationExportJobTriggerName(Provider provider, Integer importConfigurationId) {
        return "ChouetteValidationExportJobTrigger-automatic" + provider.chouetteInfo.referential + "-" + importConfigurationId;
    }

    public static @NonNull String getAutomaticChouetteValidationExportJobName(Provider provider, Integer importConfigurationId) {
        return "ChouetteValidationExportJobDetails-automatic-" + provider.chouetteInfo.referential + "-" + importConfigurationId;
    }

    public static @NonNull String getManualChouetteValidationExportJobTriggerName(Provider provider) {
        return "ChouetteValidationExportJobTrigger-manual-" + provider.chouetteInfo.referential;
    }

    public static @NonNull String getManualChouetteValidationExportJobName(Provider provider) {
        return "ChouetteValidationExportJobDetails-manual-" + provider.chouetteInfo.referential;
    }


    public static @NonNull String getPredefinedNetexFaresExportJobTriggerName(Provider provider, Long exportConfigurationId) {
        return "NetexFaresExportJobTrigger-predefined-" + provider.chouetteInfo.referential + "-" + exportConfigurationId;
    }

    public static @NonNull String getPredefinedNetexFaresExportJobName(Provider provider, Long exportConfigurationId) {
        return "NetexFaresExportJobDetails-predefined-" + provider.chouetteInfo.referential + "-" + exportConfigurationId;
    }

    /**
     * Unschedule a job from QUARTZ if it exists
     * @param jobName job to unschedule
     * @throws SchedulerException on QUARTZ exception
     */
    public void deleteJobByName(String jobName) throws SchedulerException {
        schedulerFactoryBean.start();
        JobKey jobKey = JobKey.jobKey(jobName);
        if (schedulerFactoryBean.getScheduler().deleteJob(jobKey)) {
            LOGGER.info("Deleted scheduled job {} and its trigger from QUARTZ", jobName);
        }
    }

    /**
     * Unschedule a job from QUARTZ if it exists, then schedule it again
     * @param jobDetail job to reschedule
     * @param trigger job's trigger
     * @throws SchedulerException on QUARTZ exception
     */
    public void rescheduleJob(JobDetail jobDetail, Trigger trigger) throws SchedulerException {
        schedulerFactoryBean.start();
        deleteJobByName(jobDetail.getKey().getName());
        LOGGER.info("(Re)schedule job {} into QUARTZ", jobDetail.getKey().getName());
        schedulerFactoryBean.getScheduler().scheduleJob(jobDetail, trigger);
    }

    /**
     * Find a trigger by its name
     * @param triggerName trigger to look for
     * @return trigger wrapper in {@link Optional}
     */
    public Optional<Trigger> findTriggerByName(String triggerName) {
        schedulerFactoryBean.start();
        TriggerKey key = TriggerKey.triggerKey(triggerName);
        try {
            return Optional.ofNullable(schedulerFactoryBean.getScheduler().getTrigger(key));
        } catch (SchedulerException e) {
            return Optional.empty();
        }
    }

    public Optional<Date> getNextFireTimeForTrigger(String triggerName) {
        Optional<Trigger> trigger = this.findTriggerByName(triggerName);
        return trigger
                .filter(t -> t.getNextFireTime() != null)
                .map(Trigger::getNextFireTime);
    }

}
