package no.rutebanken.marduk.routes.uttu.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UttuJobStatus {

    public Long id;

    public String status;

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }
}
