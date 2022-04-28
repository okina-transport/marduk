package no.rutebanken.marduk.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Component
public class RestDAO<T> {

    public static final String HEADER_REFERENTIAL = "x-okina-referential";
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TokenService tokenService;

    public List<T> getEntities(String url, String referential, Class<T> clazz) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List<Map>> rateResponse =
                restTemplate.exchange(url,
                        HttpMethod.GET, getEntityWithAuthenticationToken(Optional.ofNullable(referential)), new ParameterizedTypeReference<List<Map>>() {
                        });
        return rateResponse.getBody().stream().map(e -> mapper.convertValue(e, clazz)).collect(toList());
    }

    public T getEntity(String url, String referential, Class<T> clazz) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> rateResponse =
                restTemplate.exchange(url,
                        HttpMethod.GET, getEntityWithAuthenticationToken(Optional.ofNullable(referential)), new ParameterizedTypeReference<Map>() {
                        });
        return mapper.convertValue(rateResponse.getBody(), clazz);
    }

    public T updateEntity(String url, String referential, Class<T> clazz, T entityUpdated) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<T> rateResponse =
                restTemplate.exchange(url,
                        HttpMethod.PUT, updateEntityWithAuthenticationToken(Optional.ofNullable(referential), entityUpdated), clazz);
        return mapper.convertValue(rateResponse.getBody(), clazz);
    }

    private HttpEntity<String> getEntityWithAuthenticationToken(Optional<String> referential) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.getToken());
        referential.ifPresent(ref ->  headers.set(HEADER_REFERENTIAL, ref));
        return new HttpEntity<>(headers);
    }

    private HttpEntity<T> updateEntityWithAuthenticationToken(Optional<String> referential, T entityUpdated) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.getToken());
        headers.set("Content-Type", "application/json");
        referential.ifPresent(ref ->  headers.set(HEADER_REFERENTIAL, ref));
        return new HttpEntity<T>(entityUpdated, headers);
    }
}
