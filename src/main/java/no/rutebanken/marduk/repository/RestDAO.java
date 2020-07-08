package no.rutebanken.marduk.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.ExportTemplate;
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
