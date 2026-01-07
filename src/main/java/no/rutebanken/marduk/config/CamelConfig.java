package no.rutebanken.marduk.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.multipart.support.MultipartFilter;

@Configuration
public class CamelConfig {

    @Bean
    public FilterRegistrationBean<MultipartFilter> multiPartFilterRegistration() {
        FilterRegistrationBean<MultipartFilter> registrationBean =
                new FilterRegistrationBean<>();

        MultipartFilter multipartFilter = new MultipartFilter();

        registrationBean.setFilter(multipartFilter);
        registrationBean.addUrlPatterns("/services/*");
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);

        registrationBean.setName("SpringMultipartFilter");

        return registrationBean;
    }

}
