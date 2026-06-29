package no.rutebanken.marduk.services;

import no.rutebanken.marduk.domain.ImportConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static no.rutebanken.marduk.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserActionsLoggingServiceTest {

    private static final String QUEUE = "jms:queue:logging.service";
    private static final String USER_VALUE = "alice";
    private static final String ORG_VALUE = "org-test";

    @Mock
    private ProducerTemplate producer;

    @Mock
    private Exchange exchange;

    @Mock
    private Message message;

    private UserActionsLoggingService serviceEnabled;
    private UserActionsLoggingService serviceDisabled;

    @BeforeEach
    void setUp() {
        serviceEnabled = new UserActionsLoggingService(true, producer);
        serviceDisabled = new UserActionsLoggingService(false, producer);

        when(exchange.getIn()).thenReturn(message);
    }

    // -------------------------------------------------------------------------
    // recordUserAction(LogEntryDto)
    // -------------------------------------------------------------------------

    @Test
    void recordUserAction_logEntry_whenDisabled_doesNotCallProducer() {
        no.rutebanken.marduk.domain.LogEntryDto entry = new no.rutebanken.marduk.domain.LogEntryDto();
        entry.setActionType("VALIDATION");

        serviceDisabled.recordUserAction(entry);

        verifyNoInteractions(producer);
    }

    @Test
    void recordUserAction_logEntry_whenNull_doesNotCallProducer() {
        serviceEnabled.recordUserAction((no.rutebanken.marduk.domain.LogEntryDto) null);

        verifyNoInteractions(producer);
    }

    @Test
    void recordUserAction_logEntry_whenEnabled_sendsToQueue() {
        no.rutebanken.marduk.domain.LogEntryDto entry = new no.rutebanken.marduk.domain.LogEntryDto();
        entry.setActionType("VALIDATION");
        entry.setUser(USER_VALUE);
        entry.setOrganization(ORG_VALUE);

        serviceEnabled.recordUserAction(entry);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .contains("VALIDATION")
                .contains(USER_VALUE)
                .contains(ORG_VALUE);
    }

    // -------------------------------------------------------------------------
    // recordValidation
    // -------------------------------------------------------------------------

    @Test
    void recordValidation_sendsValidationEntry() {
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(OKINA_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);

        serviceEnabled.recordValidation(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .contains("VALIDATION")
                .contains(USER_VALUE)
                .contains(ORG_VALUE);
    }

    @Test
    void recordValidation_whenDisabled_doesNotCallProducer() {
        serviceDisabled.recordValidation(exchange);
        verifyNoInteractions(producer);
    }

    // -------------------------------------------------------------------------
    // recordParkingExport
    // -------------------------------------------------------------------------

    @Test
    void recordParkingExport_sendsParkingExportEntry() {
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(CHOUETTE_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);

        serviceEnabled.recordParkingExport(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("PARKING-EXPORT");
    }

    // -------------------------------------------------------------------------
    // recordPoiExport
    // -------------------------------------------------------------------------

    @Test
    void recordPoiExport_sendsPoiExportEntry() {
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(CHOUETTE_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);

        serviceEnabled.recordPoiExport(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("POI-EXPORT");
    }

    // -------------------------------------------------------------------------
    // recordStopExport
    // -------------------------------------------------------------------------

    @Test
    void recordStopExport_sendsStopExportWithMetadata() {
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(CHOUETTE_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);
        when(message.getHeader(EXPORT_EXTERNAL_IDS, String.class)).thenReturn("true");
        when(message.getHeader(EXPORT_GENERATED_MISSING_QUAYS, String.class)).thenReturn("false");

        serviceEnabled.recordStopExport(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertThat(body)
                .contains("STOP-EXPORT")
                .contains("exportExternalIds")
                .contains("exportGeneratedMissingQuays");
    }

    // -------------------------------------------------------------------------
    // recordFaresExport
    // -------------------------------------------------------------------------

    @Test
    void recordFaresExport_sendsFaresExportEntry() {
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(CHOUETTE_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);

        serviceEnabled.recordFaresExport(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("FARES-EXPORT");
    }

    // -------------------------------------------------------------------------
    // recordUserAction(Exchange) — dispatch by JSON_PART
    // -------------------------------------------------------------------------

    @Test
    void recordUserAction_exchange_whenAnalyzeIsTrue_doesNotCallProducer() {
        when(message.getHeader(ANALYZE_ACTION, Boolean.class)).thenReturn(true);

        serviceEnabled.recordUserAction(exchange);

        verifyNoInteractions(producer);
    }

    @Test
    void recordUserAction_exchange_whenJsonPartIsNull_doesNotCallProducer() {
        when(message.getHeader(ANALYZE_ACTION, Boolean.class)).thenReturn(null);
        when(message.getHeader(JSON_PART, String.class)).thenReturn(null);

        serviceEnabled.recordUserAction(exchange);

        verifyNoInteractions(producer);
    }

    @Test
    void recordUserAction_exchange_gtfsImport_dispatchesGtfsImport() {
        String jsonPart = buildJsonPart("gtfs-import");
        stubExchangeForImport(jsonPart);

        serviceEnabled.recordUserAction(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("GTFS-IMPORT");
    }

    @Test
    void recordUserAction_exchange_netexImport_dispatchesNetexImport() {
        String jsonPart = buildJsonPart("netexprofile-import");
        stubExchangeForImport(jsonPart);

        serviceEnabled.recordUserAction(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("NETEX-IMPORT");
    }

    @Test
    void recordUserAction_exchange_neptuneImport_dispatchesNeptuneImport() {
        String jsonPart = buildJsonPart("neptune-import");
        stubExchangeForImport(jsonPart);

        serviceEnabled.recordUserAction(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("NEPTUNE-IMPORT");
    }

    @Test
    void recordUserAction_exchange_gtfsExport_dispatchesGtfsExport() {
        String jsonPart = buildJsonPart("gtfs-export");
        stubExchangeForImport(jsonPart);

        serviceEnabled.recordUserAction(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("GTFS-EXPORT");
    }

    @Test
    void recordUserAction_exchange_netexExport_dispatchesNetexExport() {
        String jsonPart = buildJsonPart("netexprofile-export");
        stubExchangeForImport(jsonPart);

        serviceEnabled.recordUserAction(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("NETEX-EXPORT");
    }

    @Test
    void recordUserAction_exchange_neptuneExport_dispatchesNeptuneExport() {
        String jsonPart = buildJsonPart("neptune-export");
        stubExchangeForImport(jsonPart);

        serviceEnabled.recordUserAction(exchange);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("NEPTUNE-EXPORT");
    }

    @Test
    void recordUserAction_exchange_unknownType_doesNotCallProducer() {
        when(message.getHeader(ANALYZE_ACTION, Boolean.class)).thenReturn(null);
        when(message.getHeader(JSON_PART, String.class)).thenReturn("{\"parameters\":{\"unknown-type\":{}}}");

        serviceEnabled.recordUserAction(exchange);

        verifyNoInteractions(producer);
    }

    // -------------------------------------------------------------------------
    // recordPredefinedImport
    // -------------------------------------------------------------------------

    @Test
    void recordPredefinedImport_serializesConfigAndSends() {
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(CHOUETTE_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);

        ImportConfiguration config = new ImportConfiguration();
        config.setName("my-import");
        config.setActivated(true);

        serviceEnabled.recordPredefinedImport(exchange, config);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendBody(eq(QUEUE), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .contains("PREDEFINED-IMPORT")
                .contains("my-import");
    }

    @Test
    void recordPredefinedImport_whenDisabled_doesNotCallProducer() {
        ImportConfiguration config = new ImportConfiguration();
        config.setName("my-import");

        serviceDisabled.recordPredefinedImport(exchange, config);

        verifyNoInteractions(producer);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void stubExchangeForImport(String jsonPart) {
        when(message.getHeader(ANALYZE_ACTION, Boolean.class)).thenReturn(null);
        when(message.getHeader(JSON_PART, String.class)).thenReturn(jsonPart);
        when(message.getHeader(USER, String.class)).thenReturn(USER_VALUE);
        when(message.getHeader(CHOUETTE_REFERENTIAL, String.class)).thenReturn(ORG_VALUE);
    }

    private String buildJsonPart(String nodeKey) {
        return String.format("{\"parameters\":{\"%s\":{\"param\":\"value\"}}}", nodeKey);
    }
}