package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Utils.CipherEncryption;
import no.rutebanken.marduk.domain.ConsumerType;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.exporter.AbstractExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.GtfsExportParameters;
import no.rutebanken.marduk.services.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.*;

@Component
public class ExportToConsumersProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${marduk.upload.public.path:/tmp}")
    private String publicUploadPath;

    @Value("${simulation.ftp.url}")
    private String ftpSimulationUrl;

    @Value("${simulation.ftp.user}")
    private String ftpSimulationUser;

    @Value("${simulation.ftp.password}")
    private String ftpSimulationPassword;

    @Value("${simulation.ftp.targetDirectory}")
    private String ftpSimulationTargetDir;

    @Value("${simulation.ftp.port}")
    private Integer ftpSimulationPort;

    @Value("${simulation.export.type}")
    private String simulationExportType;

    //    @Autowired
    private static ExportJsonMapper exportJsonMapper = new ExportJsonMapper();

    @Autowired
    FtpService ftpService;

    @Autowired
    RestUploadService restUploadService;

    @Autowired
    CipherEncryption cipherEncryption;

    @Autowired
    BlobStoreService blobStoreService;

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    OpendatasoftService opendatasoftService;


    @Autowired
    private PrometheusMetricsService metrics;

    /**
     * Gets the result stream of an export  and upload it towards consumers defined for this export
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // get the json export string:
        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        String referential = exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL) != null && (Boolean) exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL) ? "mobiiti_technique" : (String) exchange.getIn().getHeaders().get(CHOUETTE_REFERENTIAL);
        Boolean exportSimulation = exchange.getIn().getHeaders().get(IS_SIMULATION_EXPORT) != null && (Boolean) exchange.getIn().getHeaders().get(IS_SIMULATION_EXPORT);
        if (StringUtils.isNotBlank(jsonExport)) {
            ExportTemplate export = exportJsonMapper.fromJson(jsonExport);
            String startDate = getValueFromParameters(exchange, "start_date").orElse(null);
            String endDate = getValueFromParameters(exchange, "end_date").orElse(null);
            log.info("Found " + export.getConsumers().size() + " for export " + export.getId() + "/" + export.getName());
            export.getConsumers().forEach(consumer -> {
                try {
                    InputStream streamToUpload = getInputStream(exchange);

                    String filePath = StringUtils.isNotEmpty(export.getExportedFileName()) ? export.getExportedFileName() : (String) exchange.getIn().getHeaders().get(EXPORT_FILE_NAME);

                    log.info("Envoi du fichier : " + filePath + " vers le consommateur : " + consumer.getName() + " - de type : " + consumer.getType().name() + " - Espace de données : " + referential);

                    try {
                        String passwordDecryptedConsumer = null;
                        if (consumer.getPassword() != null && consumer.getPassword().length > 0) {
                            passwordDecryptedConsumer = cipherEncryption.decrypt(consumer.getPassword());
                        }

                        String secretKeyDecryptedConsumer = null;
                        if (consumer.getSecretKey() != null && consumer.getSecretKey().length > 0) {
                            secretKeyDecryptedConsumer = cipherEncryption.decrypt(consumer.getSecretKey());
                        }

                        switch (consumer.getType()) {
                            case FTP:
                                ftpService.uploadStream(streamToUpload, consumer.getServiceUrl(), consumer.getLogin(), passwordDecryptedConsumer, consumer.getPort(), consumer.getDestinationPath(), filePath);
                                break;
                            case SFTP:
                                ftpService.uploadStreamSFTP(streamToUpload, consumer.getServiceUrl(), consumer.getLogin(), passwordDecryptedConsumer, consumer.getPort(), consumer.getDestinationPath(), filePath);
                                break;
                            case REST:
                                restUploadService.uploadStream(streamToUpload, consumer.getServiceUrl(), filePath, consumer.getLogin(), secretKeyDecryptedConsumer);
                                break;
                            case URL:
                                blobStoreService.uploadBlob("/" + publicUploadPath + "/" + referential + "/" + filePath, true, streamToUpload);
                                if (consumer.isNotification() && consumer.getNotificationUrls() != null && !consumer.getNotificationUrls().isEmpty()) {
                                    for (String notificationUrl : consumer.getNotificationUrls()) {
                                        notificationService.sendNotification(notificationUrl);
                                    }
                                }
                                break;
                            case OPENDATASOFT:
                                opendatasoftService.sendToOpendatasoft(streamToUpload, consumer.getServiceUrl(), consumer.getDatasetId(), secretKeyDecryptedConsumer, consumer.getExportDate(), consumer.getDescription(), filePath, startDate, endDate);

                        }
                        log.info("Envoi du fichier terminé : " + filePath + " vers le consommateur : " + consumer.getName() + " - de type : " + consumer.getType().name() + " - Espace de données : " + referential);
                        exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "OK");
                        metrics.countConsumerCalls(consumer.getType(), export.getType(), "OK");

                    } catch (IOException e) {
                        log.error("Error while getting the file before to upload to consumer " + exchange.getIn().getHeader(FILE_HANDLE, String.class), e);
                        exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "ERROR");
                        metrics.countConsumerCalls(consumer.getType(), export.getType(), "ERROR");
                    }
                } catch (Exception e) {
                    log.error("Error while uploading to consumer " + consumer.toString(), e);
                    exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "ERROR");
                    metrics.countConsumerCalls(consumer.getType(), export.getType(), "ERROR");
                }
            });

        } else if (exportSimulation) {
            log.info("Exporting simulation...");
            InputStream streamToUpload = getInputStream(exchange);
            String filePath = StringUtils.isNotEmpty((String) exchange.getIn().getHeaders().get(EXPORTED_FILENAME)) ? (String) exchange.getIn().getHeaders().get(EXPORTED_FILENAME) : (String) exchange.getIn().getHeaders().get(EXPORT_FILE_NAME);

            try {
                switch (ConsumerType.valueOf(simulationExportType)) {
                    case FTP:
                        ftpService.uploadStream(streamToUpload, ftpSimulationUrl, ftpSimulationUser, ftpSimulationPassword, ftpSimulationPort, ftpSimulationTargetDir, filePath);
                        break;
                    case SFTP:
                        ftpService.uploadStreamSFTP(streamToUpload, ftpSimulationUrl, ftpSimulationUser, ftpSimulationPassword, ftpSimulationPort, ftpSimulationTargetDir, filePath);
                        break;
                    case REST:
                        restUploadService.uploadStream(streamToUpload, ftpSimulationUrl, filePath, ftpSimulationUser, ftpSimulationPassword);
                        break;
                    case URL:
                        blobStoreService.uploadBlob("/" + publicUploadPath + "/" + referential + "/" + filePath, true, streamToUpload);
                        break;
                }
            } catch (IllegalArgumentException iae) {
                log.error("Simulation export type unknown : " + simulationExportType + ".");
                log.error("Please use one of this values : FTP, SFTP, REST or URL");
                exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "ERROR");
            }
        }
    }

    private Optional<String> getValueFromParameters(Exchange exchange, String parameterToSearch) {

        String jsonParameters = (String) exchange.getIn().getHeaders().get(JSON_PART);
        if(jsonParameters != null){
            String regex = "\"" + parameterToSearch + "\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(jsonParameters);

            if (matcher.find()) {
                String value = matcher.group(1);
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private InputStream getInputStream(Exchange exchange) throws FileNotFoundException {
        File file;
        InputStream streamToUpload;
        if (exchange.getIn().getHeader(GTFS_EXPORT_GLOBAL_OK, Boolean.class) != null &&
                exchange.getIn().getHeader(GTFS_EXPORT_GLOBAL_OK, Boolean.class).equals(true)) {
            streamToUpload = fileSystemService.getFile("mobiiti_technique/gtfs/" + exchange.getIn().getHeader(ID_FORMAT, String.class) + "/" + EXPORT_GLOBAL_GTFS_ZIP);
            exchange.getIn().setHeader(EXPORT_FILE_NAME, EXPORT_GLOBAL_GTFS_ZIP);
        } else if (exchange.getIn().getHeader(NETEX_EXPORT_GLOBAL_OK, Boolean.class) != null &&
                exchange.getIn().getHeader(NETEX_EXPORT_GLOBAL_OK, Boolean.class).equals(true)) {
            streamToUpload = fileSystemService.getFile("mobiiti_technique/netex/" + EXPORT_GLOBAL_NETEX_ZIP);
            exchange.getIn().setHeader(EXPORT_FILE_NAME, EXPORT_GLOBAL_NETEX_ZIP);

        } else if (exchange.getIn().getHeader(EXPORT_FROM_TIAMAT, Boolean.class) != null &&
                exchange.getIn().getHeader(EXPORT_FROM_TIAMAT, Boolean.class).equals(true)) {
            file = fileSystemService.getTiamatFile(exchange);
            streamToUpload = new FileInputStream(file);
        } else {
            file = fileSystemService.getOfferFile(exchange);
            streamToUpload = new FileInputStream(file);
        }
        return streamToUpload;
    }

}
