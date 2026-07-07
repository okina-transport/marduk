package no.rutebanken.marduk.config;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes Quartz jobs support {@code @Autowired} fields, on top of the
 * {@code SchedulerFactoryBean} auto-configured by Spring Boot from the
 * {@code spring.quartz.*} properties.
 */
@Configuration
public class QuartzJobFactoryConfiguration {

    @Bean
    public SchedulerFactoryBeanCustomizer autoWiringJobFactoryCustomizer(ApplicationContext applicationContext) {
        return schedulerFactoryBean -> {
            AutoWiringSpringBeanJobFactory jobFactory = new AutoWiringSpringBeanJobFactory();
            jobFactory.setApplicationContext(applicationContext);
            schedulerFactoryBean.setJobFactory(jobFactory);
        };
    }

}
