package no.rutebanken.marduk.config;

import no.rutebanken.marduk.pubsub.EnturGooglePubsubComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration
public class GooglePubSubConfig {


    @Value("${pubsub.gcs.credential.path}")
    private String credentialPath;

    @Value("${pubsub.gcs.service.url:}")
    private String serviceURL;


    @Autowired
    public void registerPubsubComponent(CamelContext camelContext, GooglePubsubComponent googlePubsub) {
        camelContext.addComponent("google-pubsub", googlePubsub);
    }


    @Bean
    public GooglePubsubComponent googlePubsubComponent() {
        GooglePubsubComponent component = new EnturGooglePubsubComponent();

        GooglePubsubConnectionFactory connectionFactory = new GooglePubsubConnectionFactory();

        connectionFactory.setCredentialsFileLocation(credentialPath);
        if (!StringUtils.isEmpty(serviceURL)) {
            connectionFactory.setServiceURL(serviceURL);
        }
        component.setConnectionFactory(connectionFactory);
        return component;
    }


}
