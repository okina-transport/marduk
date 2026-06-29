package no.rutebanken.marduk.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.rutebanken.marduk.domain.ConfigurationFtp;
import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.domain.LogContentDto;
import no.rutebanken.marduk.domain.LogEntryDto;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;

@Service
public class UserActionsLoggingService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String LOGGING_QUEUE = "jms:queue:logging.service";


    private abstract static class ConfigurationFtpMixin {
        @JsonIgnore abstract byte[] getPassword();
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addMixIn(ConfigurationFtp.class, ConfigurationFtpMixin.class);
    private static final String GTFS_IMPORT_ACTION_TYPE = "GTFS-IMPORT";
    private static final String NETEX_IMPORT_ACTION_TYPE = "NETEX-IMPORT";
    private static final String NEPTUNE_IMPORT_ACTION_TYPE = "NEPTUNE-IMPORT";
    private static final String PREDEFINED_IMPORT_ACTION_TYPE = "PREDEFINED-IMPORT";
    private static final String VALIDATION_ACTION_TYPE = "VALIDATION";
    private static final String GTFS_EXPORT_ACTION_TYPE = "GTFS-EXPORT";
    private static final String NETEX_EXPORT_ACTION_TYPE = "NETEX-EXPORT";
    private static final String NEPTUNE_EXPORT_ACTION_TYPE = "NEPTUNE-EXPORT";
    private static final String FARES_EXPORT_ACTION_TYPE = "FARES-EXPORT";
    private static final String STOP_EXPORT_ACTION_TYPE = "STOP-EXPORT";
    private static final String PARKING_EXPORT_ACTION_TYPE = "PARKING-EXPORT";
    private static final String POI_EXPORT_ACTION_TYPE = "POI-EXPORT";

    private final boolean userActionsLoggingEnabled;
    private final ProducerTemplate producer;

    public UserActionsLoggingService(@Value("${user.actions.logging.enabled:false}")boolean userActionsLoggingEnabled, ProducerTemplate producer) {
        this.userActionsLoggingEnabled = userActionsLoggingEnabled;
        this.producer = producer;
        if (!userActionsLoggingEnabled){
            logger.info("User actions logging is disabled");
        }
    }



    public void recordValidation(Exchange exchange){
        LogEntryDto logEntry = new LogEntryDto();
        logEntry.setEventTimestamp(Instant.now());
        logEntry.setActionType(VALIDATION_ACTION_TYPE);
        logEntry.setUser(exchange.getIn().getHeader(USER, String.class));
        logEntry.setOrganization(exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class));

        recordUserAction(logEntry);
    }


    public void recordParkingExport(Exchange exchange) {
        LogEntryDto logEntry = new LogEntryDto();
        logEntry.setEventTimestamp(Instant.now());
        logEntry.setActionType(PARKING_EXPORT_ACTION_TYPE);
        logEntry.setUser(exchange.getIn().getHeader(USER, String.class));
        logEntry.setOrganization(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        recordUserAction(logEntry);
    }

    public void recordPoiExport(Exchange exchange) {
        LogEntryDto logEntry = new LogEntryDto();
        logEntry.setEventTimestamp(Instant.now());
        logEntry.setActionType(POI_EXPORT_ACTION_TYPE);
        logEntry.setUser(exchange.getIn().getHeader(USER, String.class));
        logEntry.setOrganization(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        recordUserAction(logEntry);
    }

    public void recordStopExport(Exchange exchange) {
        LogEntryDto logEntry = new LogEntryDto();
        logEntry.setEventTimestamp(Instant.now());
        logEntry.setActionType(STOP_EXPORT_ACTION_TYPE);
        logEntry.setUser(exchange.getIn().getHeader(USER, String.class));
        logEntry.setOrganization(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));

        Map<String, String> exportParams = new HashMap<>();
        exportParams.put (EXPORT_EXTERNAL_IDS, exchange.getIn().getHeader(EXPORT_EXTERNAL_IDS, String.class));
        exportParams.put(EXPORT_GENERATED_MISSING_QUAYS, exchange.getIn().getHeader(EXPORT_GENERATED_MISSING_QUAYS, String.class));

        try {
            LogContentDto content = new LogContentDto();
            content.setMetadata(OBJECT_MAPPER.writeValueAsString(exportParams));
            logEntry.setLogContent(content);
        } catch (JsonProcessingException e) {
            logger.error("Error while setting content to stop export", e);
        }
        recordUserAction(logEntry);
    }

    public void recordFaresExport(Exchange exchange) {
        LogEntryDto logEntry = new LogEntryDto();
        logEntry.setEventTimestamp(Instant.now());
        logEntry.setActionType(FARES_EXPORT_ACTION_TYPE);
        logEntry.setUser(exchange.getIn().getHeader(USER, String.class));
        logEntry.setOrganization(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        recordUserAction(logEntry);

    }

    public void recordUserAction(Exchange exchange) {
        String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
        boolean analyze = exchange.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null && exchange.getIn().getHeader(ANALYZE_ACTION, Boolean.class);

        if (analyze){
            return;
        }

        if (jsonPart == null) {
            logger.warn("Unable to find user data from import");
            return;
        }

        try {
            LogEntryDto logEntry = null;
            if (jsonPart.contains("gtfs-import")){
                logEntry = createLogContentFromGtfsImport(exchange);
            }else if (jsonPart.contains("netexprofile-import")){
                logEntry = createLogContentFromNetexImport(exchange);
            }else if (jsonPart.contains("neptune-import")){
                logEntry = createLogContentFromNeptuneImport(exchange);
            }else if (jsonPart.contains("gtfs-export")){
                logEntry = createLogContentFromGtfsExport(exchange);
            }else if (jsonPart.contains("netexprofile-export")) {
                logEntry = createLogContentFromNetexExport(exchange);
            }else if (jsonPart.contains("neptune-export")) {
                logEntry = createLogContentFromNeptuneExport(exchange);
            }

           
            recordUserAction(logEntry);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON_PART header", e);
        }
    }

    private LogEntryDto createLogContentFromNeptuneExport(Exchange exchange) throws JsonProcessingException {
        return buildLogEntry(exchange, "neptune-export", NEPTUNE_EXPORT_ACTION_TYPE);
    }

    private LogEntryDto createLogContentFromNetexExport(Exchange exchange) throws JsonProcessingException {
        return buildLogEntry(exchange, "netexprofile-export", NETEX_EXPORT_ACTION_TYPE);
    }

    private LogEntryDto createLogContentFromGtfsExport(Exchange exchange) throws JsonProcessingException {
        return buildLogEntry(exchange, "gtfs-export", GTFS_EXPORT_ACTION_TYPE);
    }

    private LogEntryDto createLogContentFromNeptuneImport(Exchange exchange) throws JsonProcessingException {
        return buildLogEntry(exchange, "neptune-import", NEPTUNE_IMPORT_ACTION_TYPE);
    }

    private LogEntryDto createLogContentFromNetexImport(Exchange exchange) throws JsonProcessingException {
        return buildLogEntry(exchange, "netexprofile-import", NETEX_IMPORT_ACTION_TYPE);
    }

    private LogEntryDto createLogContentFromGtfsImport(Exchange exchange) throws JsonProcessingException {
        return buildLogEntry(exchange, "gtfs-import", GTFS_IMPORT_ACTION_TYPE);
    }

    private LogEntryDto buildLogEntry(Exchange exchange, String importNodeKey, String actionType) throws JsonProcessingException {
        String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
        JsonNode importNode = OBJECT_MAPPER.readTree(jsonPart)
                .path("parameters")
                .path(importNodeKey);

        if (importNode.isMissingNode()) {
            throw new IllegalArgumentException("Unable to find " + importNodeKey + " node");
        }

        LogContentDto logContent = new LogContentDto();
        logContent.setMetadata(importNode.toString());

        LogEntryDto logEntry = new LogEntryDto();
        logEntry.setEventTimestamp(Instant.now());
        logEntry.setActionType(actionType);
        logEntry.setUser(exchange.getIn().getHeader(USER, String.class));
        logEntry.setOrganization(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        logEntry.setLogContent(logContent);
        return logEntry;
    }

    public void recordUserAction(LogEntryDto logEntryDto) {
        if (!userActionsLoggingEnabled || logEntryDto == null){
            return;
        }
        
        try {
            producer.sendBody(LOGGING_QUEUE, OBJECT_MAPPER.writeValueAsString(logEntryDto));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize LogEntryDto to JSON", e);
        }
    }

    public void recordPredefinedImport(Exchange e, ImportConfiguration importConfiguration) {
        try {
            LogContentDto logContent = new LogContentDto();
            logContent.setMetadata(OBJECT_MAPPER.writeValueAsString(importConfiguration));

            LogEntryDto logEntry = new LogEntryDto();
            logEntry.setEventTimestamp(Instant.now());
            logEntry.setActionType(PREDEFINED_IMPORT_ACTION_TYPE);
            logEntry.setUser(e.getIn().getHeader(USER, String.class));
            logEntry.setOrganization(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
            logEntry.setLogContent(logContent);

            recordUserAction(logEntry);
        } catch (JsonProcessingException ex) {
            logger.error("Failed to serialize ImportConfiguration to JSON", ex);
        }
    }
}
