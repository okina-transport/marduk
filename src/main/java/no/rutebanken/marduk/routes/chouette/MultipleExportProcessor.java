package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.domain.Line;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.COMMERCIAL_POINT_ID_PREFIX;
import static no.rutebanken.marduk.Constants.EXPORTED_FILENAME;
import static no.rutebanken.marduk.Constants.EXPORT_END_DATE;
import static no.rutebanken.marduk.Constants.EXPORT_LINES_IDS;
import static no.rutebanken.marduk.Constants.EXPORT_NAME;
import static no.rutebanken.marduk.Constants.EXPORT_START_DATE;
import static no.rutebanken.marduk.Constants.GTFS_EXPORT_GLOBAL;
import static no.rutebanken.marduk.Constants.ID_FORMAT;
import static no.rutebanken.marduk.Constants.ID_SUFFIX;
import static no.rutebanken.marduk.Constants.KEEP_ORIGINAL_ID;
import static no.rutebanken.marduk.Constants.LINE_ID_PREFIX;
import static no.rutebanken.marduk.Constants.MAPPING_LINES_IDS;
import static no.rutebanken.marduk.Constants.NETEX_EXPORT_GLOBAL;
import static no.rutebanken.marduk.Constants.NO_GTFS_EXPORT;
import static no.rutebanken.marduk.Constants.OKINA_REFERENTIAL;
import static no.rutebanken.marduk.Constants.ORIGINAL_PROVIDER_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.STOP_ID_PREFIX;

/**
 * Handles multiple exports
 */
@Component
public class MultipleExportProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ExportTemplateDAO exportTemplateDAO;

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
        exports.stream().forEach(export -> {
            log.info("Multiple export : export => " + export.getId() + "/" + export.getName());
            try {
                if (ExportType.NETEX.equals(export.getType())) {
                    toNetexExport(export, exchange.copy(true));
                } else if (ExportType.GTFS == export.getType()) {
                    toGtfsExport(export, exchange.copy(true));
                } else if (ExportType.ARRET == export.getType()) {
                    toStopPlacesExport(export, exchange.copy(true));
                } else if (ExportType.NEPTUNE == export.getType()) {
                    toNeptuneExport(export, exchange.copy(true));
                } else {
                    log.info("Routing not supported yet for => " + export.getId() + "/" + export.getName() + "/" + export.getType());
                }
            } catch (Exception e) {
                log.error("Error while processing export " + export.getId() + "/" + export.getName(), e);
            }
        });
    }


    private void toNetexExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to NETEX export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        if("mobiiti_technique".equals(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))){
            producer.sendBodyAndHeaders("direct:chouetteNetexExportForAllProviders", exchange, exchange.getOut().getHeaders());
        }
        else{
            producer.send("activemq:queue:ChouetteExportNetexQueue", exchange);
        }
    }


    private void toNeptuneExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to NEPTUNE export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        producer.send("activemq:queue:ChouetteExportNeptuneQueue", exchange);
    }

    private void toGtfsExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to GTFS export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        String linesIds = export.getLines() != null ? StringUtils.join(export.getLines().stream().map(Line::getId).toArray(), ",") : "";
        exchange.getIn().getHeaders().put(EXPORT_LINES_IDS, linesIds);

        if (export.getStartDate() != null) {
            exchange.getIn().getHeaders().put(EXPORT_START_DATE, export.getStartDate());
        }
        if (export.getEndDate() != null) {
            exchange.getIn().getHeaders().put(EXPORT_END_DATE, export.getEndDate());
        }

        if (export.getName() != null) {
            exchange.getIn().getHeaders().put(EXPORT_NAME, export.getName());
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

        exchange.getIn().getHeaders().put(MAPPING_LINES_IDS, true);

        if("mobiiti_technique".equals(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))){
            producer.sendBodyAndHeaders("direct:chouetteGtfsExportForAllProviders", exchange, exchange.getOut().getHeaders());
        }
        else{
            producer.send("activemq:queue:ChouetteExportGtfsQueue", exchange);
        }
    }

    private void toStopPlacesExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to StopPlaces export => " + export.getId() + "/" + export.getName());
        // tiamat export is based on original referential (not the mobiiti one)
        Long tiamatProviderId = Long.valueOf(exchange.getIn().getHeaders().get(ORIGINAL_PROVIDER_ID).toString());
        exchange.getIn().getHeaders().put("tiamatProviderId", tiamatProviderId);
        prepareHeadersForExport(exchange, export);
        producer.send("activemq:queue:TiamatStopPlacesExport", exchange);

    }


    /**
     * Sets headers for export jobs
     *
     * @param exchange
     * @param export
     */
    public void prepareHeadersForExport(Exchange exchange, ExportTemplate export) throws Exception {
        boolean noGtfs = export.getType() != ExportType.GTFS;
        boolean exportGlobal = "mobiiti_technique".equals(exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        exchange.getIn().getHeaders().put(NO_GTFS_EXPORT, noGtfs);
        exchange.getIn().getHeaders().put(NETEX_EXPORT_GLOBAL, exportGlobal);
        exchange.getIn().getHeaders().put(GTFS_EXPORT_GLOBAL, exportGlobal);
        exchange.getIn().getHeaders().put(KEEP_ORIGINAL_ID, exportGlobal);
        exchange.getOut().setBody("Export id : " + export.getId());
        Map<String, Object> headers = exchange.getIn().getHeaders();
        headers.put(PROVIDER_ID, headers.get("providerId"));
        headers.put(NO_GTFS_EXPORT, noGtfs);
        headers.put(NETEX_EXPORT_GLOBAL, exportGlobal);
        headers.put(GTFS_EXPORT_GLOBAL, exportGlobal);
        headers.put(KEEP_ORIGINAL_ID, exportGlobal);
        headers.put(Constants.FILE_NAME, "export-" + export.getId() + "-" + export.getName());
        headers.put(Constants.CURRENT_EXPORT, exportJsonMapper.toJson(export));
        headers.put(EXPORTED_FILENAME, export.getExportedFileName());
        setProvidersIdsHeaders(exchange, headers);
        exchange.getOut().setHeaders(headers);
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
