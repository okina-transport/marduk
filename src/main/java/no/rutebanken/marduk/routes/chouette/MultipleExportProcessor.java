package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.*;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE;

/**
 * Handles multiple exports
 */
@Component
public class MultipleExportProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ProducerTemplate producer;

    @Autowired
    ProviderRepository providerRepository;

    @Autowired
    ExportJsonMapper exportJsonMapper;

    @Value("${superspace.name}")
    private String superspaceName;

    @Override
    public void process(Exchange exchange) {
        List<ExportTemplate> exports = (List<ExportTemplate>) exchange.getIn().getBody();
        exchange.getIn().setBody(null);
        exports.forEach(export -> {

            if (!BooleanUtils.toBoolean(export.getExportEnabled())){
                log.info("Multiple export : not launching disabled export : {}/{}", export.getId(), export.getName());
                return;
            }

            exchange.getIn().getHeaders().put(EXPORT_CONFIGURATION_ID, String.valueOf(export.getId()));

            log.info("Multiple export : export => {}/{}", export.getId(), export.getName());
            try {
                if (ExportType.NETEX.equals(export.getType())) {
                    toNetexExport(export, exchange.copy());
                } else if (ExportType.GTFS == export.getType()) {
                    toGtfsExport(export, exchange.copy());
                } else if (ExportType.ARRET == export.getType()) {
                    toStopPlacesExport(export, exchange.copy());
                } else if (ExportType.NEPTUNE == export.getType()) {
                    toNeptuneExport(export, exchange.copy());
                }else if (ExportType.POI == export.getType()) {
                    toPointsOfInterestExport(export, exchange.copy());
                } else if (ExportType.PARKING == export.getType()) {
                    toParkingsExport(export, exchange.copy());
                } else if (ExportType.NETEX_FARES == export.getType()) {
                    toNetexFaresExport(export, exchange.copy());
                } else {
                    log.info("Routing not supported yet for => {}/{}/{}", export.getId(), export.getName(), export.getType());
                }
            } catch (Exception e) {
                log.error("Error while processing export {}/{}", export.getId(), export.getName(), e);
            }
        });
    }


    private void toNetexExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to NETEX export => {}/{}", export.getId(), export.getName());
        prepareHeadersForExport(exchange, export);
        if(MOBIITI_TECHNIQUE.equals(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))){
            String referentialsNames = export.getReferentials() != null ? StringUtils.join(export.getReferentials().toArray(), ",") : "";
            exchange.getIn().getHeaders().put(EXPORT_REFERENTIALS_NAMES, StringUtils.lowerCase(referentialsNames));
            log.info("Routing to Netex export global with referentials => {}", referentialsNames);
            producer.sendBodyAndHeaders("direct:launchGlobalNetexExport", exchange, exchange.getMessage().getHeaders());
        } else {
            producer.send(ROUTE_CHOUETTE_EXPORT_NETEX_QUEUE, exchange);
        }
    }


    private void toNeptuneExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to NEPTUNE export => {}/{}", export.getId(), export.getName());
        prepareHeadersForExport(exchange, export);
        producer.send("jms:queue:ChouetteExportNeptuneQueue", exchange);
    }

    private void toGtfsExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to GTFS export => {}/{}", export.getId(), export.getName());
        prepareHeadersForExport(exchange, export);
        String linesIds = export.getLines() != null ? StringUtils.join(export.getLines().stream().map(Line::getId).toArray(), ",") : "";
        exchange.getIn().getHeaders().put(EXPORT_LINES_IDS, linesIds);

        if (export.getStartDate() != null) {
            exchange.getIn().getHeaders().put(EXPORT_START_DATE, export.getStartDate());
        }

        if (export.getEndDate() != null) {
            exchange.getIn().getHeaders().put(EXPORT_END_DATE, export.getEndDate());
        }

        if (export.getExportedFileName() != null) {
            exchange.getIn().getHeaders().put(EXPORTED_FILENAME, export.getExportedFileName());
        }

        if (export.getIdFormat() != null) {
            exchange.getIn().getHeaders().put(ID_FORMAT, export.getIdFormat().toString());
        }

        if (export.getStopIdPrefix() != null) {
            exchange.getIn().getHeaders().put(STOP_ID_PREFIX, export.getStopIdPrefix());
        }

        if (export.getLineIdPrefix() != null) {
            exchange.getIn().getHeaders().put(LINE_ID_PREFIX, export.getLineIdPrefix());
        }

        if (export.getIdSuffix() != null) {
            exchange.getIn().getHeaders().put(ID_SUFFIX, export.getIdSuffix());
        }

        if (export.getCommercialPointIdPrefix() != null) {
            exchange.getIn().getHeaders().put(COMMERCIAL_POINT_ID_PREFIX, export.getCommercialPointIdPrefix());
        }

        if (export.getCommercialPointExport() != null) {
            exchange.getIn().getHeaders().put(COMMERCIAL_POINT_EXPORT, export.getCommercialPointExport());
        }

        if (export.getGoogleMapsCompatibility() != null) {
            exchange.getIn().getHeaders().put(GOOGLE_MAPS_COMPATIBILITY, export.getGoogleMapsCompatibility());
        }

        if (export.getUseExtendedGtfsRouteTypes() != null) {
            exchange.getIn().getHeaders().put(USE_EXTENDED_GTFS_ROUTE_TYPES, export.getUseExtendedGtfsRouteTypes());
        }

        if (export.getAttributionsExportModes() != null) {
            exchange.getIn().getHeaders().put(EXPORT_ATTRIBUTIONS, export.getAttributionsExportModes().toString());
        }

        if (export.getPostProcess() != null){
            exchange.getIn().getHeaders().put(POST_PROCESS, export.getPostProcess());
        }

        if (export.getAgencyId() != null){
            exchange.getIn().getHeaders().put(AGENCY_ID, export.getAgencyId());
        }

        if (export.getAgencyName() != null){
            exchange.getIn().getHeaders().put(AGENCY_NAME, export.getAgencyName());
        }

        if (export.getAgencyTimezone() != null){
            exchange.getIn().getHeaders().put(AGENCY_TIMEZONE, export.getAgencyTimezone());
        }

        if (export.getAgencyLang() != null) {
            exchange.getIn().getHeaders().put(AGENCY_LANG, export.getAgencyLang());
        }

        if (export.getAgencyURL() != null){
            exchange.getIn().getHeaders().put(AGENCY_URL, export.getAgencyURL());
        }

        exchange.getIn().getHeaders().put(EXPORT_ALL_LINES, export.isExportAllLines());
        exchange.getIn().getHeaders().put(MAPPING_LINES_IDS, true);

        if(MOBIITI_TECHNIQUE.equals(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))){
            String referentialsNames = export.getReferentials() != null ? StringUtils.join(export.getReferentials().toArray(), ",") : "";
            exchange.getIn().getHeaders().put(EXPORT_REFERENTIALS_NAMES, StringUtils.lowerCase(referentialsNames));
            log.info("Routing to GTFS export global with referentials => {}", referentialsNames);
            producer.sendBodyAndHeaders("direct:chouetteGtfsExportForAllProviders", exchange, exchange.getMessage().getHeaders());
        }
        else{
            producer.send("jms:queue:ChouetteExportGtfsQueue", exchange);
        }
    }

    private void toStopPlacesExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to StopPlaces export => {}/{}", export.getId(), export.getName());
        // tiamat export is based on original referential (not the mobiiti one)
        Long tiamatProviderId = Long.valueOf(exchange.getIn().getHeaders().get(ORIGINAL_PROVIDER_ID).toString());
        exchange.getIn().getHeaders().put("tiamatProviderId", tiamatProviderId);
        prepareHeadersForExport(exchange, export);
        producer.send("jms:queue:TiamatStopPlacesExport", exchange);

    }

    private void toPointsOfInterestExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to Points of Interest export => {}/{}", export.getId(), export.getName());
        // tiamat export is based on original referential (not the mobiiti one)
        Long tiamatProviderId = Long.valueOf(exchange.getIn().getHeaders().get(ORIGINAL_PROVIDER_ID).toString());
        exchange.getIn().getHeaders().put("tiamatProviderId", tiamatProviderId);
        prepareHeadersForExport(exchange, export);
        producer.send("jms:queue:TiamatPointOfInterestExport", exchange);
    }

    private void toParkingsExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to Parkings export => {}/{}", export.getId(), export.getName());
        // tiamat export is based on original referential (not the mobiiti one)
        Long tiamatProviderId = Long.valueOf(exchange.getIn().getHeaders().get(ORIGINAL_PROVIDER_ID).toString());
        exchange.getIn().getHeaders().put("tiamatProviderId", tiamatProviderId);
        prepareHeadersForExport(exchange, export);
        producer.send("jms:queue:TiamatParkingsExport", exchange);
    }

    private void toNetexFaresExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to NETEX fares export => {}/{}", export.getId(), export.getName());
        prepareHeadersForExport(exchange, export);
        producer.send("jms:queue:NetexFaresPredefinedExport", exchange);
    }


    /**
     * Sets headers for export jobs
     *
     * @param exchange
     * @param export
     */
    public void prepareHeadersForExport(Exchange exchange, ExportTemplate export) throws Exception {
        boolean noGtfs = export.getType() != ExportType.GTFS;
        boolean exportGlobal = MOBIITI_TECHNIQUE.equals(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        exchange.getIn().getHeaders().put(EXPORT_NAME, export.getName());
        exchange.getIn().getHeaders().put(NO_GTFS_EXPORT, noGtfs);
        exchange.getIn().getHeaders().put(NETEX_EXPORT_GLOBAL, exportGlobal);
        exchange.getIn().getHeaders().put(GTFS_EXPORT_GLOBAL, exportGlobal);
        exchange.getIn().getHeaders().put(IS_SIMULATION_EXPORT, false);
        boolean keepOriginalId = true;
        if (!exportGlobal){
            keepOriginalId = !IdFormat.SOURCE.equals(export.getIdFormat());
        }
        exchange.getIn().getHeaders().put(KEEP_ORIGINAL_ID, keepOriginalId);
        exchange.getOut().setBody("Export id : " + export.getId());
        Map<String, Object> headers = exchange.getIn().getHeaders();
        headers.put(PROVIDER_ID, headers.get("providerId") != null ? headers.get("providerId") : exchange.getIn().getHeader(PROVIDER_ID));
        headers.put(NO_GTFS_EXPORT, noGtfs);
        headers.put(NETEX_EXPORT_GLOBAL, exportGlobal);
        headers.put(GTFS_EXPORT_GLOBAL, exportGlobal);
        headers.put(IS_SIMULATION_EXPORT, false);
        headers.put(KEEP_ORIGINAL_ID, keepOriginalId);
        if (StringUtils.isNotBlank(export.getExportedFileName())){
            headers.put(FILE_NAME, export.getExportedFileName());
        }else{
            headers.put(FILE_NAME, "export-" + export.getId() + "-" + export.getName());
        }

        headers.put(CURRENT_EXPORT, exportJsonMapper.toJson(export));
        headers.put(EXPORT_GENERATED_MISSING_QUAYS, export.isExportGeneratedMissingQuays());
        headers.put(EXPORT_EXTERNAL_IDS, export.isExportExternalIds());
        headers.put(EXPORTED_FILENAME, export.getExportedFileName());
        if (export.getPostProcess() != null){
            headers.put(POST_PROCESS, export.getPostProcess());
        }
        setProvidersIdsHeaders(exchange, headers);
        exchange.getMessage().setHeaders(headers);
    }


    public void setProvidersIdsHeaders(Exchange exchange, Map<String, Object> headers) {
        Provider provider = providerRepository.getProvider(exchange.getIn().getHeader(PROVIDER_ID, Long.class));
        Provider mobiitiProvider;
        if (isMobiitiProvider(provider.name)) {
            mobiitiProvider = provider;
            provider = providerRepository.findByName(provider.name.replace(superspaceName + "_", ""));
        } else {
            mobiitiProvider = providerRepository.getMobiitiProvider(provider.getId());
        }

        headers.put(CHOUETTE_REFERENTIAL, mobiitiProvider.chouetteInfo.getReferential());
        headers.put(OKINA_REFERENTIAL, mobiitiProvider.chouetteInfo.getReferential());
        headers.put(PROVIDER_ID, mobiitiProvider.getId());
        headers.put("providerId", mobiitiProvider.getId());
        headers.put(ORIGINAL_PROVIDER_ID, provider.getId());
    }

    private boolean isMobiitiProvider(String name) {
        return name.startsWith(superspaceName + "_");
    }
}
