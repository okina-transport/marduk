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

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static no.rutebanken.marduk.Constants.*;

/**
 * Handles multiple exports
 */
@Component
public class MultipleExportProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    public static final String MOSAIC_REFERENTIAL = "MOSAIC_REFERENTIAL";

    @Value("${stop-places-export.api.url}")
    private String stopPlacesExportUrl;

    @Autowired
    ExportTemplateDAO exportTemplateDAO;

    @Autowired
    ProducerTemplate producer;

    @Autowired
    ProviderRepository providerRepository;

    @Autowired
    ExportJsonMapper exportJsonMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        List<ExportTemplate> exports = (List<ExportTemplate>) exchange.getIn().getBody();
        exchange.getIn().setBody(null);
        updateExportWithMatchingMosaicLines(exports, exchange);
        exports.stream().forEach(export -> {
            log.info("Multiple export : export => " + export.getId() + "/" + export.getName());
            try {
                if (ExportType.NETEX.equals(export.getType())) {
                    toNetexExport(export, exchange);
                } else if (ExportType.GTFS == export.getType()) {

                    toGtfsExport(export, exchange);
                } else if (ExportType.ARRET == export.getType()) {
                    toStopPlacesExport(export, exchange);
                } else {
                    log.info("Routing not supported yet for => " + export.getId() + "/" + export.getName() + "/" + export.getType());
                }
            } catch(Exception e) {
                log.error("Error while processing export " + export.getId() + "/" + export.getName(), e);
            }
        });
    }





    private void toNetexExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to NETEX export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        producer.send("activemq:queue:ChouetteExportNetexQueue", exchange);
    }

    private void toGtfsExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to GTFS export => " + export.getId() + "/" + export.getName());
        prepareHeadersForExport(exchange, export);
        String linesIds = export.getLines() != null ? StringUtils.join(export.getLines().stream().map(Line::getId).toArray(), ",") : "";
        exchange.getIn().getHeaders().put(EXPORT_LINES_IDS, linesIds);

        if (export.getStartDate() != null) {
            exchange.getIn().getHeaders().put(EXPORT_START_DATE, Timestamp.valueOf(export.getStartDate()).getTime() / 1000);
        }
        if (export.getEndDate() != null) {
            exchange.getIn().getHeaders().put(EXPORT_END_DATE, Timestamp.valueOf(export.getEndDate()).getTime() / 1000);
        }
        producer.send("activemq:queue:ChouetteExportGtfsQueue", exchange);
    }

    private void toStopPlacesExport(ExportTemplate export, Exchange exchange) throws Exception {
        log.info("Routing to StopPlaces export => " + export.getId() + "/" + export.getName());
        // tiamat export is based on original referential (not the mosaic one)
        Long tiamatProviderId = Long.valueOf(exchange.getIn().getHeaders().get(ORIGINAL_PROVIDER_ID).toString());
        exchange.getIn().getHeaders().put("tiamatProviderId", tiamatProviderId);
        prepareHeadersForExport(exchange, export);
        producer.send("direct:tiamatStopPlacesExport", exchange);

    }


    /**
     * Sets headers for export jobs
     * @param exchange
     * @param export
     */
    public void prepareHeadersForExport(Exchange exchange, ExportTemplate export) throws Exception {
        boolean noGtfs = export.getType() != ExportType.GTFS;
        exchange.getIn().getHeaders().put(NO_GTFS_EXPORT, noGtfs);
        exchange.getOut().setBody("Export id : " + export.getId());
        Map<String, Object> headers = new HashMap<>();
        headers.putAll(exchange.getIn().getHeaders());
        headers.put(PROVIDER_ID, headers.get("providerId"));
        headers.put(NO_GTFS_EXPORT, noGtfs);
        headers.put(Constants.FILE_NAME, "export-" + export.getId() + "-" + export.getName());
        headers.put(Constants.CURRENT_EXPORT, exportJsonMapper.toJson(export));
        setProvidersIdsHeaders(exchange, headers);
        exchange.getOut().setHeaders(headers);
    }


    public void setProvidersIdsHeaders(Exchange exchange, Map<String, Object> headers) {
        Provider provider = providerRepository.getProvider(exchange.getIn().getHeader(PROVIDER_ID, Long.class));
        Provider mosaicProvider;
        if(provider.isMosaicProvider()) {
            mosaicProvider = provider;
            provider = providerRepository.findByName(provider.name.replace("mosaic_", ""));
        }
        else {
            mosaicProvider = providerRepository.getMosaicProvider(provider.getId());
        }

        headers.put(CHOUETTE_REFERENTIAL, mosaicProvider.chouetteInfo.getReferential());
        headers.put(OKINA_REFERENTIAL, mosaicProvider.chouetteInfo.getReferential());
        headers.put(PROVIDER_ID, mosaicProvider.getId());
        headers.put("providerId", mosaicProvider.getId());
        headers.put(ORIGINAL_PROVIDER_ID, provider.getId());
    }



    /**
     * Remplace les lignes des exports par les lignes correspondantes dans la filiale Mosaic
     * @param exports
     * @param exchange
     */
    public void updateExportWithMatchingMosaicLines(List<ExportTemplate> exports, Exchange exchange) {
        Provider provider = providerRepository.getProvider(exchange.getIn().getHeader(ORIGINAL_PROVIDER_ID, Long.class));
        List<ExportTemplate> mosaicLinesExports = exportTemplateDAO.getAll(provider.chouetteInfo.referential);
        exports.stream().filter(e -> ExportType.GTFS.equals(e.getType())).forEach( export -> {
            Optional<ExportTemplate> mosaicLinesExport = mosaicLinesExports.stream().filter(e -> e.getId().equals(export.getId())).findAny();
            mosaicLinesExport.ifPresent(me -> {
                List<Line> matchingMosaicLines = (List<Line>) export.getLines().stream().map(l -> {
                    return me.getLines().stream().filter(mel -> l.getObjectId().equals(mel.getObjectId())).findAny().get();
                }).collect(toList());
                export.setLines(matchingMosaicLines);
            });
        });
    }
}
