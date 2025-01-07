package no.rutebanken.marduk.security;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class TokenService {

    public String getToken() {
        return "";
    }
}
