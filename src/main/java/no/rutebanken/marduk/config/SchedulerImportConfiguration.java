package no.rutebanken.marduk.config;

import no.rutebanken.marduk.routes.ImportConfigurationJob;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;


@Configuration
@EnableAutoConfiguration
public class SchedulerImportConfiguration {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${spring.quartz.jobStore.driverDelegateClass}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String user;

    @Value("${spring.datasource.password}")
    private String password;


    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public SchedulerFactoryBean getSchedulerImportConfiguration() {
        logger.info("Starting import configuration Scheduler...");

        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        schedulerFactory.setConfigLocation(new ClassPathResource("import-configuration-quartz.properties"));
        schedulerFactory.setSchedulerName("QuartzScheduler-Import-Configuration");
        schedulerFactory.setJobFactory(springBeanJobFactory());

        schedulerFactory.setDataSource(quartzDataSource());
        return schedulerFactory;
    }

    @Bean
    public SpringBeanJobFactory springBeanJobFactory() {
        AutoWiringSpringBeanJobFactory jobFactory = new AutoWiringSpringBeanJobFactory();
        logger.debug("Configuring Job factory");

        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public JobDetailFactoryBean getJobImportConfiguration() {
        logger.info("Get import configuration job...");
        JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
        jobDetailFactory.setJobClass(ImportConfigurationJob.class);
        jobDetailFactory.setName("ImportConfigurationJob");
        jobDetailFactory.setDescription("Invoke import configuration Job service...");
        jobDetailFactory.setDurability(true);
        return jobDetailFactory;
    }

    @Bean
    public JobDetailFactoryBean resumeJobImportConfiguration() throws SchedulerException {
        SchedulerFactoryBean schedulerFactory = getSchedulerImportConfiguration();
        JobDetailFactoryBean importConfigurationJobDetails = getJobImportConfiguration();
        schedulerFactory.start();

        if (schedulerFactory.getScheduler().checkExists(importConfigurationJobDetails.getObject().getKey())) {
            schedulerFactory.getScheduler().resumeJob(importConfigurationJobDetails.getObject().getKey());
            logger.info("Import configuration job is started...");
        }
        return importConfigurationJobDetails;
    }

    @Bean
    @QuartzDataSource
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource quartzDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        return dataSource;
    }

}
