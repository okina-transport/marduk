package no.rutebanken.marduk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
public class OAuth2Config {

    private static final String CLIENT_PROPERTY_KEY = "spring.security.oauth2.client.registration.keycloak";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new CustomClientRegistrationRepository();
    }

    private ClientRegistration getClientRegistration() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("your-client-id")
                .clientSecret("your-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://your-keycloak-domain/auth/realms/your-realm/protocol/openid-connect/auth")
                .tokenUri("https://your-keycloak-domain/auth/realms/your-realm/protocol/openid-connect/token")
                .userInfoUri("https://your-keycloak-domain/auth/realms/your-realm/protocol/openid-connect/userinfo")
                .jwkSetUri("https://your-keycloak-domain/auth/realms/your-realm/protocol/openid-connect/certs")
                .clientName("Keycloak")
                .build();
    }

    private class CustomClientRegistrationRepository implements ClientRegistrationRepository {
        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            if ("keycloak".equals(registrationId)) {
                return getClientRegistration();
            }
            return null;
        }
    }
}