package no.rutebanken.marduk.config;

import no.rutebanken.marduk.routes.ConcertoJob;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
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
public class SchedulerConcertoConfig {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${spring.quartz.jobStore.driverDelegateClass}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String user;

    @Value("${spring.datasource.username}")
    private String password;


    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public SchedulerFactoryBean getSchedulerConcerto() {
        logger.info("Starting Concerto Scheduler...");

        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        schedulerFactory.setConfigLocation(new ClassPathResource("concerto-quartz.properties"));
        schedulerFactory.setSchedulerName("QuartzScheduler-Concerto");
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
    public JobDetailFactoryBean getJobConcerto() {
        logger.info("Get Concerto job...");
        JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
        jobDetailFactory.setJobClass(ConcertoJob.class);
        jobDetailFactory.setName("ConcertoJob");
        jobDetailFactory.setDescription("Invoke Concerto Job service...");
        jobDetailFactory.setDurability(true);
        return jobDetailFactory;
    }

    @Bean
    public JobDetailFactoryBean resumeJobConcerto() throws SchedulerException {
        SchedulerFactoryBean schedulerFactory = getSchedulerConcerto();
        JobDetailFactoryBean concertoJobDetails = getJobConcerto();
        schedulerFactory.start();

        if (schedulerFactory.getScheduler().checkExists(concertoJobDetails.getObject().getKey())) {
            schedulerFactory.getScheduler().resumeJob(concertoJobDetails.getObject().getKey());
            logger.info("Concerto job is started...");
        }
        return concertoJobDetails;
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
