package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.*;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import no.rutebanken.marduk.routes.chouette.json.exporter.FileToConsumerInfo;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.TokenService;
import no.rutebanken.marduk.services.*;
import no.rutebanken.marduk.utils.CipherEncryption;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.repository.RestDAO.HEADER_REFERENTIAL;


@Component
public class ExportToConsumersProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    private static ExportJsonMapper exportJsonMapper = new ExportJsonMapper();

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

    @Value("${export-templates.api.url}")
    private String exportTemplatesUrl;

    @Value("${pigma.upload.path}")
    private String pigmaUploadPath;

    private final FtpService ftpService;

    private final RestUploadService restUploadService;

    private final CipherEncryption cipherEncryption;

    private final BlobStoreService blobStoreService;

    private final FileSystemService fileSystemService;

    private final NotificationService notificationService;

    private final OpendatasoftService opendatasoftService;

    private final PrometheusMetricsService metrics;

    private final TokenService tokenService;

    private final KafkaService kafkaService;

    public ExportToConsumersProcessor(FtpService ftpService, RestUploadService restUploadService, CipherEncryption cipherEncryption, BlobStoreService blobStoreService, FileSystemService fileSystemService, NotificationService notificationService, OpendatasoftService opendatasoftService, PrometheusMetricsService metrics, TokenService tokenService, KafkaService kafkaService) {
        this.ftpService = ftpService;
        this.restUploadService = restUploadService;
        this.cipherEncryption = cipherEncryption;
        this.blobStoreService = blobStoreService;
        this.fileSystemService = fileSystemService;
        this.notificationService = notificationService;
        this.opendatasoftService = opendatasoftService;
        this.metrics = metrics;
        this.tokenService = tokenService;
        this.kafkaService = kafkaService;
    }

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
        String referential = BooleanUtils.isTrue((Boolean) exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL)) ?
                "mobiiti_technique" : (String) exchange.getIn().getHeaders().get(CHOUETTE_REFERENTIAL);
        boolean exportSimulation = BooleanUtils.isTrue((Boolean) exchange.getIn().getHeaders().get(IS_SIMULATION_EXPORT));
        if (StringUtils.isNotBlank(jsonExport)) {
            ExportTemplate export = exportJsonMapper.fromJson(jsonExport);
            List<String> uploadInfo = new ArrayList<>(export.getConsumers().size());
            log.info("Found {} for export {}/{}", export.getConsumers().size(), export.getId(), export.getName());
            export.getConsumers().forEach(consumer -> {
                try {
                    InputStream streamToUpload = getInputStream(exchange);

                    String filePath = StringUtils.isNotEmpty(export.getExportedFileName()) ? export.getExportedFileName() : (String) exchange.getIn().getHeaders().get(EXPORT_FILE_NAME);

                    log.info("Envoi du fichier : {} vers le consommateur : {} - de type {} - Espace de données {}",filePath, consumer.getName(), consumer.getType().name(), referential);

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
                                Pair<String, String> workingDates = getWorkingDates(referential);
                                String startDate = workingDates.getLeft();
                                String endDate = workingDates.getRight();
                                opendatasoftService.sendToOpendatasoft(streamToUpload, consumer.getServiceUrl(), consumer.getDatasetId(), secretKeyDecryptedConsumer, consumer.getExportDate(), consumer.getDescription(), filePath, startDate, endDate, consumer.isAppendDescription());
                                break;
                            case PIGMA:
                                String nameFilePigma = referential + "-aggregated-" + export.getType().toString().toLowerCase() + ".zip";
                                blobStoreService.uploadBlob("/" + pigmaUploadPath + "/" + nameFilePigma, true, streamToUpload);
                                break;
                        }
                        FileToConsumerInfo fileToConsumerInfo = new FileToConsumerInfo(consumer.getType().name(), "OK", consumer.getName());
                        uploadInfo.add(fileToConsumerInfo.toString());
                        log.info("Envoi du fichier terminé : {} vers le consommateur : {} - de type {} - Espace de données {}", filePath, consumer.getName(), consumer.getType().name(), referential);
                        exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, "OK");
                        Set<String> operators = getOperators(exchange, export);
                        metrics.countConsumerCalls(consumer.getType(), export.getType(), "OK", operators);
                        kafkaService.sendExportToConsumerStatusToKafka(new ExportToConsumerStatusDto(consumer.getType(), export.getType(), true, operators));
                    } catch (IOException e) {
                        log.error("Error while getting the file before upload to consumer {}", exchange.getIn().getHeader(FILE_HANDLE, String.class), e);
                        exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, JobEvent.State.FAILED.name());
                        Set<String> operators = getOperators(exchange, export);
                        metrics.countConsumerCalls(consumer.getType(), export.getType(), JobEvent.State.FAILED.name(), operators);
                        kafkaService.sendExportToConsumerStatusToKafka(new ExportToConsumerStatusDto(consumer.getType(), export.getType(), false, operators));
                        FileToConsumerInfo fileToConsumerInfo = new FileToConsumerInfo(consumer.getType().name(), JobEvent.State.FAILED.name(), consumer.getName());
                        uploadInfo.add(fileToConsumerInfo.toString());
                    }
                } catch (Exception e) {
                    log.error("Error while uploading to consumer {}", consumer, e);
                    exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, JobEvent.State.FAILED.name());
                    Set<String> operators = getOperators(exchange, export);
                    metrics.countConsumerCalls(consumer.getType(), export.getType(), JobEvent.State.FAILED.name(), operators);
                    kafkaService.sendExportToConsumerStatusToKafka(new ExportToConsumerStatusDto(consumer.getType(), export.getType(), false, operators));
                    FileToConsumerInfo fileToConsumerInfo = new FileToConsumerInfo(consumer.getType().name(), JobEvent.State.FAILED.name(),consumer.getName());
                    uploadInfo.add(fileToConsumerInfo.toString());
                }
            });

            exchange.getIn().setHeader(EXPORT_TO_CONSUMER_DATA, String.join(";", uploadInfo));

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
                    default:
                        break;
                }
            } catch (IllegalArgumentException iae) {
                log.error("Simulation export type unknown : {}.", simulationExportType);
                log.error("Please use one of this values : FTP, SFTP, REST or URL");
                exchange.getIn().setHeader(EXPORT_TO_CONSUMER_STATUS, JobEvent.State.FAILED.name());
            }
        }
    }

    private static Set<String> getOperators(Exchange exchange, ExportTemplate export) {
        Set<String> operators = exchange.getIn().getHeader(JOB_OPERATORS, Set.class);
        if (export.getType() == ExportType.GTFS || export.getType() == ExportType.NETEX || export.getType() == ExportType.NEPTUNE || export.getType() == ExportType.ARRET) {
            operators = Set.of("Semitan");
        }
        return operators;
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
            StringBuilder response = new StringBuilder();

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
