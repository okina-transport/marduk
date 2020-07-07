package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;

@Component
public class RestDAO<T> {

    @Autowired
    private TokenService tokenService;

    public List<T> getEntities(String url) {
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<List<T>> rateResponse =
                restTemplate.exchange(url,
                        HttpMethod.GET, getEntityWithAuthenticationToken(), new ParameterizedTypeReference<List<T>>() {
                        });
        return rateResponse.getBody();
    }

    private HttpEntity<String> getEntityWithAuthenticationToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.getToken());
        return new HttpEntity<>(headers);
    }
}
