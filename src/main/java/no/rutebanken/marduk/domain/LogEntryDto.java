package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;


public class LogEntryDto {

    @JsonProperty("event_timestamp")
    private Instant eventTimestamp;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("user")
    private String user;

    @JsonProperty("object_id")
    private String objectId;

    @JsonProperty("organization")
    private String organization;

    @JsonProperty("service")
    private String service = "MARDUK";

    @JsonProperty("log_content")
    private LogContentDto logContent;

    public LogEntryDto() {
    }

    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setUser(String user) { this.user = user; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public void setOrganization(String organization) { this.organization = organization; }
    public void setLogContent(LogContentDto logContent) { this.logContent = logContent; }
}