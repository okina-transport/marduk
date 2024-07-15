package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Utils.CipherEncryption;
import no.rutebanken.marduk.domain.ConsumerType;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.OrganisationView;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.exporter.AbstractExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.GtfsExportParameters;
import no.rutebanken.marduk.security.TokenService;
import no.rutebanken.marduk.services.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.repository.RestDAO.HEADER_REFERENTIAL;

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

    @Value("${export-templates.api.url}")
    private String exportTemplatesUrl;


    @Autowired
    TokenService tokenService;

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

            log.info("Found " + export.getConsumers().size() + " for export " + export.getId() + "/" + export.getName());
            Pair<String, String> workingDates = getWorkingDates( referential);
            String startDate = workingDates.getLeft();
            String endDate = workingDates.getRight();
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
                                opendatasoftService.sendToOpendatasoft(streamToUpload, consumer.getServiceUrl(), consumer.getDatasetId(), secretKeyDecryptedConsumer, consumer.getExportDate(), consumer.getDescription(), filePath, startDate, endDate, consumer.isAppendDescription());

                        }
                        log.info("Envoi du fichier terminé : " + filePath + " vers le consommateur : " + consumer.getName() + " - de type : " + consumer.getType().name() + " - Espace de données : " + referential);
                        exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "OK");
                        metrics.countConsumerCalls(consumer.getType(), export.getType(), "OK");

                    } catch (IOException e) {
                        log.error("Error while getting the file before to upload to consumer " + exchange.getIn().getHeader(FILE_HANDLE, String.class), e);
                        exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "FAILED");
                        metrics.countConsumerCalls(consumer.getType(), export.getType(), "FAILED");
                    }
                } catch (Exception e) {
                    log.error("Error while uploading to consumer " + consumer.toString(), e);
                    exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "FAILED");
                    metrics.countConsumerCalls(consumer.getType(), export.getType(), "FAILED");
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
                exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "FAILED");
            }
        }
    }

    private Pair<String, String> getWorkingDates(String referential) {

        LocalDate now = LocalDate.now();
        String startDate = now.getYear() + "-" + now.getMonthValue() + "-" + now.getDayOfMonth();
        int nextYear = now.getYear() + 1;
        String endDate = nextYear + "-" + now.getMonthValue() + "-" + now.getDayOfMonth();


        Optional<OrganisationView> schemasInfosOpt = getSchemasInfos(referential);
        if (schemasInfosOpt.isPresent()) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            OrganisationView organisationView = schemasInfosOpt.get();
            startDate = formatter.format(organisationView.getProductionInfos().getStartDate());
            endDate = formatter.format(organisationView.getProductionInfos().getEndDate());


        }
        return Pair.of(startDate, endDate);
    }

    private Optional<OrganisationView> getSchemasInfos(String referential) {
        String schemaInfoUrl = exportTemplatesUrl.replace("export-templates", "schemas/schema-info");
        try {
            URL obj = new URL(schemaInfoUrl);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Accept", "application/json, text/plain, */*");
            con.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7");
            con.setRequestProperty(HEADER_REFERENTIAL, referential);
            con.setRequestProperty("Authorization", "Bearer " + tokenService.getToken());

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();


            ObjectMapper objectMapper = new ObjectMapper();
            OrganisationView organisationView = objectMapper.readValue(response.toString(), OrganisationView.class);
            return Optional.of(organisationView);

        } catch (Exception e) {
            log.error("Error while requesting schema informations", e);
            return Optional.empty();
        }
    }

    private Optional<String> getValueFromParameters(Exchange exchange, String parameterToSearch) {

        String jsonParameters = (String) exchange.getIn().getHeaders().get(JSON_PART);
        if (jsonParameters != null) {
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
