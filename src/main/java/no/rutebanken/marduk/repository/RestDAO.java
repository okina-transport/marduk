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
import java.util.Optional;

@Component
public class RestDAO<T> {

    public static final String HEADER_REFERENTIAL = "x-okina-referential";

    @Autowired
    private TokenService tokenService;

    public List<T> getEntities(String url, String referential) {
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<List<T>> rateResponse =
                restTemplate.exchange(url,
                        HttpMethod.GET, getEntityWithAuthenticationToken(Optional.ofNullable(referential)), new ParameterizedTypeReference<List<T>>() {
                        });
        return rateResponse.getBody();
    }


    private HttpEntity<String> getEntityWithAuthenticationToken(Optional<String> referential) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.getToken());
        referential.ifPresent(ref ->  headers.set(HEADER_REFERENTIAL, ref));
        return new HttpEntity<>(headers);
    }

    private HttpEntity<String> getEntityWithAuthenticationToken() {
        return getEntityWithAuthenticationToken(Optional.empty());
    }
}
