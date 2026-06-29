package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LogContentDto {

    @JsonProperty("metadata")
    private String metadata;

    @JsonProperty("object_before")
    private String objectBefore;

    @JsonProperty("object_after")
    private String objectAfter;

    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setObjectBefore(String objectBefore) { this.objectBefore = objectBefore; }
    public void setObjectAfter(String objectAfter) { this.objectAfter = objectAfter; }
}
