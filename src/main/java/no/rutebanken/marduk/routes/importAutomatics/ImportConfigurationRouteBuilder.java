package no.rutebanken.marduk.routes.importAutomatics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jcraft.jsch.*;
import no.rutebanken.marduk.Utils.CipherEncryption;
import no.rutebanken.marduk.Utils.SendMail;
import no.rutebanken.marduk.config.SchedulerImportConfiguration;
import no.rutebanken.marduk.domain.ConfigurationFtp;
import no.rutebanken.marduk.domain.ConfigurationUrl;
import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.domain.ImportParameters;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.domain.Recipient;
import no.rutebanken.marduk.exceptions.AutomaticImportException;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.routes.ImportConfigurationJob;
import no.rutebanken.marduk.routes.MyAuthenticator;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.zip.ZipException;

import static no.rutebanken.marduk.Constants.*;

@Component
public class ImportConfigurationRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${client.name}")
    private String client;

    @Value("${server.name}")
    private String server;

    public static final String ERROR_INTERNAL = "Erreur interne";
    public static final String ERROR_URL_CONNEXION_FAIL = "La connexion à l'URL a échoué, veuillez vérifier que l'URL renseignée est bien valide et que le serveur est démarré";
    public static final String ERROR_FTP_PORT_INVALID = "La configuration FTP est incorrect: port invalide";
    public static final String ERROR_FTP_USERNAME_OR_URL_INVALID = "La configuration FTP est incorrect: nom d'utilisateur et/ou URL invalide ";
    public static final String ERROR_FTP_CONNEXION_FAIL = "Connexion au serveur FTP impossible, veuillez vérifier que la configuration FTP est correct et que le serveur est démarré";
    public static final String ERROR_FTP_AUTHENTICATION = "Erreur d'authentification au serveur FTP";
    public static final String ERROR_RETRIEVING_IMPORT_FILE = "Erreur lors de la récupération du fichier d'import";
    public static final String ERROR_ACCESSING_IMPORT_FOLDER_OR_FILE = "Erreur lors de l'accès au dossier/fichier d'import sur le serveur FTP, veuillez vérifier que le dossier/fichier d'import existent bien";
    public static final String ERROR_NO_FTP_OR_URL_CONFIGURATION = "L'import automatique n'a pas de configuration FTP ou URL définie, veuillez mettre à jour l'import en vous connectant sur Mobi-iti";
    public static final String ERROR_NO_WORKFLOW_AND_NOT_NETEX_IMPORT = "La configuration de l'import est incorrect, il doit soit avoir un workflow, soit être un import de type Netex parking, Netex POI ou Netex arrêt";
    public static final String ERROR_INVALID_NETEX_POI_OR_PARKING_OR_STOP_PLACE_ZIP = "Le fichier ZIP NeTEx POI ou parking ou arrêts à importer est invalide, il doit contenir seulement un fichier XML";

    private final SchedulerImportConfiguration schedulerImportConfiguration;
    private final CipherEncryption cipherEncryption;
    private final ImportConfigurationDAO importConfigurationDAO;
    private final SendMail sendMail;
    private final FileSystemService fileSystemService;

    public ImportConfigurationRouteBuilder(SchedulerImportConfiguration schedulerImportConfiguration,
                                           CipherEncryption cipherEncryption,
                                           ImportConfigurationDAO importConfigurationDAO,
                                           SendMail sendMail,
                                           FileSystemService fileSystemService) {
        this.schedulerImportConfiguration = schedulerImportConfiguration;
        this.cipherEncryption = cipherEncryption;
        this.importConfigurationDAO = importConfigurationDAO;
        this.sendMail = sendMail;
        this.fileSystemService = fileSystemService;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ImportConfigurationQueue?transacted=true")
                .streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting import configuration for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> handleImportConfigurationQueueMessage(e))
                .choice()
                    .when(header(CONTINUE_IMPORT).isEqualTo(Boolean.TRUE))
                        .to("direct:importLaunch")
                .endChoice()
                .routeId("import-configuration-job");

        from("direct:updateSchedulerImportConfiguration")
                .log(LoggingLevel.INFO, getClass().getName(), "Update scheduler Import configuration")
                .process(this::updateSchedulerImportConfiguration)
                .routeId("update-scheduler-process-import-configuration");

        from("direct:deleteSchedulerImportConfiguration")
                .log(LoggingLevel.INFO, getClass().getName(), "Delete scheduler Import configuration")
                .process(this::deleteSchedulerImportConfiguration)
                .routeId("delete-scheduler-process-import-configuration");

        from("direct:getCron")
                .log(LoggingLevel.INFO, getClass().getName(), "Get scheduler Import Configuration")
                .process(this::getCron)
                .routeId("get-cron-scheduler-process-import-configuration");
    }

    private void handleImportConfigurationQueueMessage(Exchange e) {
        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
        String referential = e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String importConfigurationId = e.getIn().getHeader(IMPORT_CONFIGURATION_ID, String.class);
        ImportConfiguration importConfiguration = importConfigurationDAO.getImportConfiguration(referential, importConfigurationId);

        if (!importConfiguration.isActivated()) {
            log.warn("{} Import is inactive (id : {})", correlation(), importConfigurationId);
            log.warn("{} Abort automatic import", correlation());
            sendMailForImportFailure(importConfiguration, referential, "L'import est inactif, veuillez l'activer en vous connectant sur Mobi-iti");
            return;
        }

        if (importConfiguration.getWorkflow() == null && !Arrays.asList(FileType.NETEX_PARKING.name(), FileType.NETEX_POI.name(), FileType.NETEX_STOP_PLACE.name()).contains(importConfiguration.getImportParameters().get(0).getImportType())) {
            log.error("{} Import configuration is incorrect (id : {})", correlation(), importConfigurationId);
            log.error("{} It should either have a workflow defined or be a NeTEx parking, POI or stop place import", correlation());
            log.warn("{} Abort automatic import", correlation());
            sendMailForImportFailure(importConfiguration, referential, ERROR_NO_WORKFLOW_AND_NOT_NETEX_IMPORT);
            return;
        }

        e.getIn().setHeader(IMPORT_CONFIGURATION, importConfiguration);
        e.getIn().setHeader(IS_ACTIVE, importConfiguration.isActivated());
        parseImportParameters(e, importConfiguration);
        e.getIn().setHeader(CONTINUE_IMPORT, Boolean.FALSE);
        Optional<FileItem> importFile = Optional.empty();
        try {
            if (!CollectionUtils.isEmpty(importConfiguration.getConfigurationFtpList())) {
                importFile = retrieveImportFromFtp(referential, importConfiguration, importConfiguration.getConfigurationFtpList().get(0));
            } else if (!CollectionUtils.isEmpty(importConfiguration.getConfigurationUrlList())) {
                importFile = retrieveImportFromUrl(referential, importConfiguration, importConfiguration.getConfigurationUrlList().get(0));
            } else {
                log.error("{} No FTP or URL configuration set for import", correlation());
                log.warn("{} Abort automatic import", correlation());
                sendMailForImportFailure(importConfiguration, referential, ERROR_NO_FTP_OR_URL_CONFIGURATION);
                return;
            }
        } catch (AutomaticImportException ex) {
            log.error(String.format("%s Error retrieving import file", correlation()), ex);
            log.warn("{} Abort automatic import", correlation());
            sendMailForImportFailure(importConfiguration, referential, ex.getMessage());
        } catch (Exception ex) {
            log.error(String.format("%s Error retrieving import file not handled correctly, " +
                    "make sure to catch this error and wrap it into an AutomaticImportException " +
                    "with an explicit error message for client", correlation()), ex);
            log.warn("{} Abort automatic import", correlation());
            sendMailForImportFailure(importConfiguration, referential, ERROR_INTERNAL);
        }

        if (importFile.isPresent()) {
            FileItem importFileItem = importFile.get();
            if (Arrays.asList(FileType.NETEX_POI.name(), FileType.NETEX_STOP_PLACE.name(), FileType.NETEX_PARKING.name())
                    .contains(importConfiguration.getImportParameters().get(0).getImportType())
            && importFileItem.getName().endsWith(".zip")) {
                try {
                    importFileItem = fileSystemService.unzipNetexZip(importFileItem);
                } catch (ZipException ex) {
                    log.error(String.format("%s Invalid NeTEx POI or parking or stop place ZIP file", correlation()), ex);
                    log.warn("{} Abort automatic import", correlation());
                    sendMailForImportFailure(importConfiguration, referential, ERROR_INVALID_NETEX_POI_OR_PARKING_OR_STOP_PLACE_ZIP);
                    return;
                } catch (IOException ex) {
                    log.error(String.format("%s Error unzipping NeTEx zip file", correlation()), ex);
                    log.warn("{} Abort automatic import", correlation());
                    sendMailForImportFailure(importConfiguration, referential, ERROR_INTERNAL);
                    return;
                }
            }
            try {
                // updates last_timestamp from url or ftp configuration
                importConfigurationDAO.update(referential, importConfiguration);
            } catch (JsonProcessingException ex) {
                log.error(String.format("%s Error updating import configuration", correlation()), ex);
                log.warn("{} Import configuration last timestamp will not be updated", correlation());
                log.info("{} Continue automatic import", correlation());
            }
            e.getIn().setBody(importFileItem);
            e.getIn().setHeader(CONTINUE_IMPORT, Boolean.TRUE);
            e.getIn().setHeader(FOLDER_NAME, referential);
        }
    }

    private Optional<FileItem> retrieveImportFromFtp(String referential, ImportConfiguration importConfiguration, ConfigurationFtp configurationFtp) throws AutomaticImportException {
        if (configurationFtp.getType().equals("FTP")) {
            return getFileFromFTP(referential, importConfiguration, configurationFtp);
        } else if (configurationFtp.getType().equals("SFTP")) {
            return getFileFromSFTP(referential, importConfiguration, configurationFtp);
        } else {
            log.error("{} Invalid FTP configuration type {} (should be 'FTP' or 'STFP')", correlation(), configurationFtp.getType());
            throw new AutomaticImportException("Configuration de l'import automatique incorrect, veuillez supprimer puis recréer la configuration FTP/STFP");
        }
    }

    private Optional<FileItem> getFileFromSFTP(String referential, ImportConfiguration importConfiguration, ConfigurationFtp configurationFtp) throws AutomaticImportException {
        log.info("Retrieve import file from SFTP (url: {})", configurationFtp.getUrl());

        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            try {
                session = new JSch().getSession(configurationFtp.getLogin(), configurationFtp.getUrl(), Math.toIntExact(configurationFtp.getPort()));
            } catch (ArithmeticException e) {
                throw new AutomaticImportException(ERROR_FTP_PORT_INVALID, e);
            }  catch (JSchException e) {
                log.error("{} SFTP username or host invalid", correlation());
                throw new AutomaticImportException(ERROR_FTP_USERNAME_OR_URL_INVALID, e);
            }
            String decryptedPassword = decryptPassword(configurationFtp.getPassword());
            session.setPassword(decryptedPassword);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            try {
                session.connect();
                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
            } catch (JSchException e) {
                log.error("{} Error connecting to SFTP server (url: {})", correlation(), configurationFtp.getUrl());
                throw new AutomaticImportException(ERROR_FTP_CONNEXION_FAIL, e);
            }
            log.info("Connected to the SFTP server");
            if (StringUtils.isNotEmpty(configurationFtp.getFolder())) {
                try {
                    channelSftp.cd(configurationFtp.getFolder());
                } catch (SftpException e) {
                    log.error("{} Change directory failed on STFP serveur (url: {}, folder: {})", correlation() , configurationFtp.getUrl(), configurationFtp.getFolder());
                    throw new AutomaticImportException(ERROR_ACCESSING_IMPORT_FOLDER_OR_FILE, e);
                }
            }
            try (InputStream inputStream = channelSftp.get(configurationFtp.getFilename())) {
                LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(channelSftp.stat(configurationFtp.getFilename()).getMTime()), TimeZone.getDefault().toZoneId());
                File targetFile = new File(configurationFtp.getFilename());
                try {
                    FileUtils.copyInputStreamToFile(inputStream, targetFile);
                } catch (IOException e) {
                    log.error("{} Error copying file from STFP server", correlation());
                    throw new AutomaticImportException(ERROR_RETRIEVING_IMPORT_FILE, e);
                }
                if (targetFile.length() > 0) {
                    if (configurationFtp.getLastTimestamp() == null || localDateTime.isAfter(configurationFtp.getLastTimestamp())) {
                        configurationFtp.setLastTimestamp(localDateTime);
                        return Optional.of(copyFileFromInputStream(new FileInputStream(targetFile), configurationFtp.getFilename()));
                    } else {
                        log.warn("No new file to import for the dataspace : {} for the import configuration SFTP : {}", referential, configurationFtp.getUrl());
                        sendMailForFileAlreadyImported(importConfiguration, referential, configurationFtp.getFilename());
                        return Optional.empty();
                    }
                } else {
                    log.error("File {} not found for the dataspace {}", configurationFtp.getFilename(), referential);
                    sendMailForFileNotFound(importConfiguration, referential, configurationFtp.getFilename());
                    return Optional.empty();
                }
            } catch (SftpException | IOException e) {
                log.error("{} Error retrieving file from STFP server (url: {}, folder: {}, filename: {})", correlation(), configurationFtp.getUrl(), configurationFtp.getFolder(), configurationFtp.getFilename());
                throw new AutomaticImportException(ERROR_RETRIEVING_IMPORT_FILE, e);
            }
        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
                channelSftp.disconnect();
            }
            if (session != null) session.disconnect();
        }
    }

    private Optional<FileItem> getFileFromFTP(String referential, ImportConfiguration importConfiguration, ConfigurationFtp configurationFtp) throws AutomaticImportException {
        log.info("Retrieve import file from FTP (url: {})", configurationFtp.getUrl());

        FTPClient client = new FTPClient();
        client.configure(new FTPClientConfig());
        try {
            // connection
            try {
                client.connect(configurationFtp.getUrl(), Math.toIntExact(configurationFtp.getPort()));
            } catch (ArithmeticException e) {
                throw new AutomaticImportException(ERROR_FTP_PORT_INVALID, e);
            } catch (IOException e) {
                throw new AutomaticImportException(ERROR_FTP_CONNEXION_FAIL, e);
            }

            // authentication
            if (StringUtils.isNotEmpty(configurationFtp.getLogin()) && ArrayUtils.isNotEmpty(configurationFtp.getPassword())) {
                try {
                    if (!client.login(configurationFtp.getLogin(), decryptPassword(configurationFtp.getPassword()))) {
                        throw new AutomaticImportException(ERROR_FTP_AUTHENTICATION);
                    }
                } catch (IOException e) {
                    throw new AutomaticImportException(ERROR_FTP_AUTHENTICATION, e);
                }
            }
            log.info("Connected to FTP server");
            client.enterLocalPassiveMode();
            FTPFile[] files;
            try {
                files = client.listFiles(configurationFtp.getFolder());
            } catch (IOException e) {
                log.error("{} Error listing files from FTP server", correlation());
                throw new AutomaticImportException(ERROR_ACCESSING_IMPORT_FOLDER_OR_FILE, e);
            }
            Optional<FTPFile> optionalFTPFile = Arrays.stream(files).filter(ftpFile -> ftpFile.getName().equals(configurationFtp.getFilename())).findFirst();
            if (optionalFTPFile.isPresent()) {
                FTPFile file = optionalFTPFile.get();
                LocalDateTime localDateTime = LocalDateTime.ofInstant(file.getTimestamp().toInstant(), file.getTimestamp().getTimeZone().toZoneId());
                if (configurationFtp.getLastTimestamp() == null || localDateTime.isAfter(configurationFtp.getLastTimestamp())) {
                    configurationFtp.setLastTimestamp(localDateTime);
                    try (InputStream importFile = client.retrieveFileStream(configurationFtp.getFolder() + "/" + file.getName())){
                        return Optional.of(copyFileFromInputStream(importFile, file.getName()));
                    } catch (IOException e) {
                        log.error("{} Error retrieving file from FTP server", correlation());
                        throw new AutomaticImportException(ERROR_RETRIEVING_IMPORT_FILE, e);
                    }
                } else {
                    log.warn("{} No new file to import for the dataspace : {} for the import configuration FTP : {}", correlation(), referential, configurationFtp.getUrl());
                    sendMailForFileAlreadyImported(importConfiguration, referential, configurationFtp.getFilename());
                    return Optional.empty();
                }
            } else {
                log.error("{} File {} not found for the dataspace {}", correlation(), configurationFtp.getFilename(), referential);
                sendMailForFileNotFound(importConfiguration, referential, configurationFtp.getFilename());
                return Optional.empty();
            }
        } finally {
            try {
                client.disconnect();
            } catch (IOException e) {
                log.error(String.format("%s Error disconnecting from FTP client", correlation()), e);
            }
        }
    }

    private Optional<FileItem> retrieveImportFromUrl(String referential, ImportConfiguration importConfiguration, ConfigurationUrl configurationUrl) throws AutomaticImportException {
        log.info("{} Retrieve import file from URL: {}", correlation(), configurationUrl.getUrl());

        // handle authentication
        if (StringUtils.isNotEmpty(configurationUrl.getLogin()) && ArrayUtils.isNotEmpty(configurationUrl.getPassword())) {
            String decryptedPassword = decryptPassword(configurationUrl.getPassword());
            try {
                Authenticator.setDefault(new MyAuthenticator(configurationUrl.getLogin(), decryptedPassword));
            } catch (SecurityException e) {
                log.error("{} Error setting default authenticator", correlation());
                throw new AutomaticImportException(ERROR_INTERNAL, e);
            }
        }
        trustAllCertificates();

        // try connection with server
        URL importFileUrl = parseUrl(configurationUrl.getUrl());
        HttpURLConnection importFileUrlConnection;
        int responseCode;
        try {
            importFileUrlConnection = (HttpURLConnection) importFileUrl.openConnection();
            importFileUrlConnection.connect();
            responseCode = importFileUrlConnection.getResponseCode();
        } catch (IOException e) {
            log.error("{} Error opening connection to {}", correlation(), importFileUrl);
            throw new AutomaticImportException(ERROR_URL_CONNEXION_FAIL, e);
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            log.error("{} File not found for the dataspace : {} for the import configuration URL : {} (responseCode: {})", correlation(), referential, configurationUrl.getUrl(), responseCode);
            sendMailForFileNotFound(importConfiguration, referential, importFileUrl.getPath().substring(importFileUrl.getPath().lastIndexOf('/') + 1));
            return Optional.empty();
        }

        // get import last modified time
        LocalDateTime importLastTimeModified = getImportModificationDateFromUrl(configurationUrl, importFileUrlConnection);
        if (configurationUrl.getLastTimestamp() != null && configurationUrl.getLastTimestamp().isAfter(importLastTimeModified)) {
            log.warn("{} No new file to import for the referential : {} for the import configuration URL : {}", correlation(), referential, configurationUrl.getUrl());
            sendMailForFileAlreadyImported(importConfiguration, referential, importFileUrl.getPath().substring(importFileUrl.getPath().lastIndexOf('/') + 1));
            return Optional.empty();
        }

        try {
            configurationUrl.setLastTimestamp(importLastTimeModified);
            Optional<FileItem> importFile = Optional.of(downloadImportFileFromUrl(importFileUrl));
            importFileUrlConnection.disconnect();
            return importFile;
        } catch (IOException e) {
            log.error("{} Error downloading import file from {}", correlation(), importFileUrl);
            throw new AutomaticImportException(ERROR_RETRIEVING_IMPORT_FILE, e);
        }
    }

    /**
     * @param encryptedPassword password to decrypt
     * @return decrypted user password
     * @throws AutomaticImportException when password decryption fails
     */
    private String decryptPassword(byte[] encryptedPassword) throws AutomaticImportException {
        try {
            return cipherEncryption.decrypt(encryptedPassword);
        } catch (Exception e) {
            log.error("{} Error decrypting password", correlation());
            throw new AutomaticImportException(ERROR_INTERNAL, e);
        }
    }

    /**
     * @param configurationUrl URL configuration
     * @param httpURLConnection opened connection to server
     * @return modification date of import file from server
     * @throws AutomaticImportException when connection to URL info server fails or JSON parsing of URL info server response fails
     */
    private LocalDateTime getImportModificationDateFromUrl(ConfigurationUrl configurationUrl, HttpURLConnection httpURLConnection) throws AutomaticImportException {
        if (StringUtils.isEmpty(configurationUrl.getUrlInfo())) {
            long date = httpURLConnection.getLastModified();
            if (date == 0) {
                // take current time instead of 1970/01/01 00:00:00 to trigger
                // next import if date is 0 too
                return LocalDateTime.now();
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(date), TimeZone.getDefault().toZoneId());
        }
        URL urlInfo = parseUrl(configurationUrl.getUrlInfo());
        try (InputStream inputStreamUrlInfo = urlInfo.openStream()) {
            JSONParser jsonParser = new JSONParser();
            org.json.simple.JSONObject jsonObject;
            try {
                jsonObject = (org.json.simple.JSONObject) jsonParser.parse(new InputStreamReader(inputStreamUrlInfo, StandardCharsets.UTF_8));
            } catch (IOException | ParseException e) {
                log.error("{} Error parsing JSON from {}", correlation(), urlInfo);
                throw new AutomaticImportException(ERROR_INTERNAL, e);
            }
            String stringDateLastModified = jsonObject.get("updated").toString();
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(stringDateLastModified);
            return offsetDateTime.toLocalDateTime();
        } catch (IOException e) {
            log.error("{} Error opening connection to {}", correlation(), urlInfo);
            throw new AutomaticImportException(ERROR_INTERNAL, e);
        }
    }

    /**
     * @param url string to parse
     * @return URL object from url string
     * @throws AutomaticImportException when URL parsing fails, i.e. url string is malformed
     */
    private URL parseUrl(String url) throws AutomaticImportException {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            log.error("{} Invalid URL : {}", correlation(), url);
            throw new AutomaticImportException(String.format("URL invalide (url :%s)", url), e);
        }
    }

    private void parseImportParameters(Exchange e, ImportConfiguration importConfiguration) {
        ImportParameters importParameters = !importConfiguration.getImportParameters().isEmpty() ? importConfiguration.getImportParameters().get(0) : null;
        if (importParameters != null) {
            e.getIn().setHeader(ANALYZE_ACTION, true);
            e.getIn().setHeader(FILE_TYPE, importParameters.getImportType());
            e.getIn().setHeader(IMPORT_TYPE, importParameters.getImportType());
            e.getIn().setHeader(SPLIT_CHARACTER, importParameters.getSplitCharacter());
            e.getIn().setHeader(ROUTE_MERGE, importParameters.getRouteMerge());
            e.getIn().setHeader(GENERATE_MAP_MATCHING, importParameters.getGenerateMapMatching());
            e.getIn().setHeader(ROUTES_REORGANIZATION, importParameters.getRoutesReorganization());
            e.getIn().setHeader(ROUTE_SORT_ORDER, importParameters.getRouteSortOrder());
            e.getIn().setHeader(NETEX_IMPORT_LAYOUTS, importParameters.getNetexImportLayouts());
            e.getIn().setHeader(NETEX_IMPORT_COLORS, importParameters.getNetexImportColors());
            e.getIn().setHeader(CLEAN_MODE, importParameters.getImportMode());
            e.getIn().setHeader(KEEP_BOARDING_ALIGHTING_POSSIBILITY, importParameters.getKeepBoardingAlighting());
            e.getIn().setHeader(KEEP_STOP_GEOLOCALISATION, importParameters.getKeepStopGeolocalisation());
            e.getIn().setHeader(KEEP_STOP_NAMES, importParameters.getKeepStopNames());
            e.getIn().setHeader(IMPORT_SHAPES_FILE, importParameters.getImportShapesFile());
            e.getIn().setHeader(COMMERCIAL_POINT_ID_PREFIX_TO_REMOVE, importParameters.getCommercialPointPrefixToRemove());
            e.getIn().setHeader(QUAY_ID_PREFIX_TO_REMOVE, importParameters.getQuayIdPrefixToRemove());
            e.getIn().setHeader(STOP_AREA_PREFIX_TO_REMOVE, importParameters.getStopAreaPrefixToRemove());
            e.getIn().setHeader(AREA_CENTROID_PREFIX_TO_REMOVE, importParameters.getAreaCentroidPrefixToRemove());
            e.getIn().setHeader(LINE_PREFIX_TO_REMOVE, importParameters.getLinePrefixToRemove());
            e.getIn().setHeader(IGNORE_COMMERCIAL_POINTS, importParameters.getIgnoreCommercialPoints());
            e.getIn().setHeader(IMPORT_MODE, importParameters.getImportMode());
            e.getIn().setHeader(WORKLOW, importConfiguration.getWorkflow() != null ? importConfiguration.getWorkflow().toString() : null);
            e.getIn().setHeader(REMOVE_PARENT_STATIONS, importParameters.getRemoveParentStations());
            e.getIn().setHeader(UPDATE_STOP_ACCESSIBILITY, importParameters.getUpdateStopAccessibility());
            e.getIn().setHeader(RAIL_UIC_PROCESSING, importParameters.getRailUICprocessing());
            e.getIn().setHeader(DISTANCE_GEOLOCATION, importParameters.getDistanceGeolocation());
            e.getIn().setHeader(DESCRIPTION, importParameters.getDescription());
            e.getIn().setHeader(USE_TARGET_NETWORK, importParameters.getUseTargetNetwork());
            e.getIn().setHeader(TARGET_NETWORK, importParameters.getTargetNetwork());
            StringBuilder recipients = new StringBuilder();
            for (Recipient recipient : importConfiguration.getRecipients()) {
                recipients.append(recipient.getEmail());
                recipients.append(",");
            }
            if (StringUtils.isNotEmpty(recipients.toString())) {
                e.getIn().setHeader(RECIPIENTS, recipients.toString());
            }
        }
    }

    private FileItem downloadImportFileFromUrl(URL url) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            String fileName = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
            return copyFileFromInputStream(inputStream, fileName);
        }
    }

    private void trustAllCertificates() {
        // Create a new trust manager that trust all certificates
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }
        }};

        // Activate the new trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception exception) {
            log.error("Certificate management error to download a file from a URL :", exception);
        }
    }

    /**
     * Send a mail to warn that file was not found
     *
     * @param importConfiguration the configuration of the automatic import
     * @param referential         the referential for which file was not found
     * @param filename            the file name that was not found
     */
    private void sendMailForFileNotFound(ImportConfiguration importConfiguration, String referential, String filename) {

        String mailObject = "[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - import automatique - Fichier non trouve";
        LocalDate now = LocalDate.now();
        String text = "Bonjour, <br>" +
                "Après vérification, il n'y pas de nouvelle offre à intégrer pour la date du " + now + ". <br>" +
                "Nom de l'import : " + importConfiguration.getName() + " <br>" +
                "Nom du fichier : " + filename + " <br>" +
                "Organisation : " + referential + " <br>" +
                "Cordialement,<br>" +
                "L'équipe Mobi-iti";

        for (Recipient recipient : importConfiguration.getRecipients()) {
            sendMail.sendEmail(mailObject, recipient.getEmail(), text, null);
        }

    }

    /**
     * Send a mail to warn that import failed
     *
     * @param importConfiguration the configuration of the automatic import
     * @param referential         the referential for which file was already imported
     * @param errorMessage        the cause of the error
     */
    private void sendMailForImportFailure(ImportConfiguration importConfiguration, String referential, String errorMessage) {

        String mailObject = "[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - import automatique - Erreur lors de la récupération du fichier";
        LocalDate now = LocalDate.now();
        String text = "Bonjour, <br>" +
                "L'import automatique du " + now + " a échoué lors de la récupération du fichier à importer.<br>" +
                "Nom de l'import : " + importConfiguration.getName() + " <br>" +
                "Organisation : " + referential + " <br>" +
                "Message d'erreur: " + errorMessage + " <br>" +
                "Cordialement,<br>" +
                "L'équipe Mobi-iti";

        for (Recipient recipient : importConfiguration.getRecipients()) {
            sendMail.sendEmail(mailObject, recipient.getEmail(), text, null);
        }
    }

    /**
     * Send a mail to warn that file was already imported
     *
     * @param importConfiguration the configuration of the automatic import
     * @param referential         the referential for which file was already imported
     * @param filename            the file name that was already imported
     */
    private void sendMailForFileAlreadyImported(ImportConfiguration importConfiguration, String referential, String filename) {

        String mailObject = "[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - import automatique - Fichier deja importe";
        LocalDate now = LocalDate.now();
        String text = "Bonjour, <br>" +
                "Après vérification, il n'y pas de nouvelle offre à intégrer pour la date du " + now + ", le fichier ayant déjà été importé précédemment. <br>" +
                "Nom de l'import : " + importConfiguration.getName() + " <br>" +
                "Nom du fichier : " + filename + " <br>" +
                "Organisation : " + referential + " <br>" +
                "Cordialement,<br>" +
                "L'équipe Mobi-iti";

        for (Recipient recipient : importConfiguration.getRecipients()) {
            sendMail.sendEmail(mailObject, recipient.getEmail(), text, null);
        }
    }

    private FileItem copyFileFromInputStream(InputStream inputStream, String outputFilename) throws IOException {
        String contentType = "text/xml";
        if (!outputFilename.endsWith(".xml")) {
            contentType = "application/zip";
            outputFilename = StringUtils.appendIfMissing(outputFilename, ".zip");
        }

        java.io.File file = new File(outputFilename);
        FileItemFactory fac = new DiskFileItemFactory();
        FileItem fileItem = fac.createItem("file", contentType, false, file.getName());
        Streams.copy(inputStream, fileItem.getOutputStream(), true);

        return fileItem;
    }

    private void getCron(Exchange e) throws SchedulerException, JSONException {
        SchedulerFactoryBean scheduler = schedulerImportConfiguration.getSchedulerImportConfiguration();
        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        Integer importConfigurationId = e.getIn().getHeader(IMPORT_CONFIGURATION_ID, Integer.class);
        CronTrigger trigger = (CronTrigger) scheduler.getScheduler().getTrigger(TriggerKey.triggerKey("ImportConfigurationJobTrigger-" + provider.chouetteInfo.referential + "-" + importConfigurationId));
        if (trigger != null) {
            String[] dateFromCron = trigger.getCronExpression().split(" ");
            JSONObject jsonObject = new JSONObject();
            jsonObject.append("hour", dateFromCron[2]);
            jsonObject.append("minutes", dateFromCron[1]);
            jsonObject.append("date", trigger.getStartTime().getTime());
            jsonObject.append("applicationDays", dateFromCron[5]);
            e.getIn().setBody(jsonObject.toString());
        }
    }

    private void updateSchedulerImportConfiguration(Exchange e) throws SchedulerException {
        Map headers = (Map) e.getIn().getBody(Map.class).get("headers");
        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        if (headers != null) {
            if (headers.get(IMPORT_CONFIGURATION_SCHEDULER) != null && provider.chouetteInfo.referential != null && headers.get(IMPORT_CONFIGURATION_ID) != null) {
                SchedulerFactoryBean scheduler = schedulerImportConfiguration.getSchedulerImportConfiguration();

                String importConfigurationSchedulerCron = (String) headers.get(IMPORT_CONFIGURATION_SCHEDULER);
                Integer importConfigurationId = (Integer) headers.get(IMPORT_CONFIGURATION_ID);

                if (StringUtils.isNotEmpty(importConfigurationSchedulerCron)) {
                    String[] dateFromCron = importConfigurationSchedulerCron.split(" ");

                    Date startDate = getStartDate();

                    importConfigurationSchedulerCron = dateFromCron[0] + " " + dateFromCron[1] + " " + dateFromCron[2] + " ? * " + dateFromCron[5] + " " + dateFromCron[6];

                    JobDetail importConfigurationJobDetails = JobBuilder.newJob(ImportConfigurationJob.class).withIdentity("ImportConfigurationJobDetails-" + provider.chouetteInfo.referential + "-" + importConfigurationId).storeDurably(true).build();

                    Trigger importConfigurationTrigger = TriggerBuilder.newTrigger().forJob(importConfigurationJobDetails).withIdentity("ImportConfigurationJobTrigger-" + provider.chouetteInfo.referential + "-" + importConfigurationId).withSchedule(CronScheduleBuilder.cronSchedule(importConfigurationSchedulerCron).withMisfireHandlingInstructionDoNothing()).startAt(startDate).build();

                    scheduler.start();

                    if (scheduler.getScheduler().checkExists(importConfigurationJobDetails.getKey())) {
                        scheduler.getScheduler().deleteJob(importConfigurationJobDetails.getKey());
                    }

                    scheduler.getScheduler().scheduleJob(importConfigurationJobDetails, importConfigurationTrigger);

                    log.info("Import Configuration Scheduler for " + provider.chouetteInfo.referential + "-" + importConfigurationId + " created with cron expression: " + importConfigurationSchedulerCron);
                } else {
                    deleteScheduler(provider, scheduler, importConfigurationId);
                }
            }
        }
    }

    private void deleteScheduler(Provider provider, SchedulerFactoryBean scheduler, Integer importConfigurationId) throws SchedulerException {
        JobDetail importConfigurationJobDetails = JobBuilder.newJob(ImportConfigurationJob.class).withIdentity("ImportConfigurationJobDetails-" + provider.chouetteInfo.referential + "-" + importConfigurationId).build();
        if (scheduler.getScheduler().checkExists(importConfigurationJobDetails.getKey())) {
            scheduler.getScheduler().deleteJob(importConfigurationJobDetails.getKey());
            log.info("Import Configuration Scheduler for {}-{} deleted", provider.chouetteInfo.referential, importConfigurationId);
        } else {
            log.info("Import Configuration Scheduler for {}-{} not deleted because not existed", provider.chouetteInfo.referential, importConfigurationId);
        }
    }

    private void deleteSchedulerImportConfiguration(Exchange e) throws SchedulerException {
        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        Integer importConfigurationId = e.getIn().getHeader(IMPORT_CONFIGURATION_ID, Integer.class);
        if (provider.chouetteInfo.referential != null && importConfigurationId != null) {
            SchedulerFactoryBean scheduler = schedulerImportConfiguration.getSchedulerImportConfiguration();
            deleteScheduler(provider, scheduler, importConfigurationId);
        }
    }

    /**
     * @return the start date of the cron : 1st day of the current month
     */
    private Date getStartDate() {
        Calendar currentDate = Calendar.getInstance();
        int month = currentDate.get(Calendar.MONTH);
        int year = currentDate.get(Calendar.YEAR);

        Calendar startDate = Calendar.getInstance();
        startDate.set(Calendar.DAY_OF_MONTH, 1);
        startDate.set(Calendar.MONTH, month);
        startDate.set(Calendar.YEAR, year);
        return startDate.getTime();
    }
}

