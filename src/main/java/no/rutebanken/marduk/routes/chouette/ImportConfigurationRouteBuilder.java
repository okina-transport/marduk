package no.rutebanken.marduk.routes.chouette;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import no.rutebanken.marduk.Utils.CipherEncryption;
import no.rutebanken.marduk.Utils.SendMail;
import no.rutebanken.marduk.config.SchedulerImportConfiguration;
import no.rutebanken.marduk.domain.ConfigurationFtp;
import no.rutebanken.marduk.domain.ConfigurationUrl;
import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.domain.ImportParameters;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.domain.Recipient;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.routes.ImportConfigurationJob;
import no.rutebanken.marduk.routes.MyAuthenticator;
import no.rutebanken.marduk.services.BlobStoreService;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

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

import static no.rutebanken.marduk.Constants.*;

@Component
public class ImportConfigurationRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    SchedulerImportConfiguration schedulerImportConfiguration;

    @Autowired
    CipherEncryption cipherEncryption;

    @Autowired
    ImportConfigurationDAO importConfigurationDAO;

    @Autowired
    SendMail sendMail;

    @Autowired
    BlobStoreService blobStoreService;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ImportConfigurationQueue?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting import configuration for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
                    String referential = e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
                    String importConfigurationId = e.getIn().getHeader(IMPORT_CONFIGURATION_ID, String.class);
                    ImportConfiguration importConfiguration = importConfigurationDAO.getImportConfiguration(referential, importConfigurationId);
                    e.getIn().setHeader(IMPORT_CONFIGURATION, importConfiguration);
                    e.getIn().setHeader(IS_ACTIVE, importConfiguration.isActivated());
                })
                .choice()
                    .when(header(IS_ACTIVE).isEqualTo(true))
                        .process(e -> {
                            ImportConfiguration importConfiguration = e.getIn().getHeader(IMPORT_CONFIGURATION, ImportConfiguration.class);
                            String referential = e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
                            // FTP
                            for (ConfigurationFtp configurationFtp : importConfiguration.getConfigurationFtpList()) {
                                if ("FTP".equals(configurationFtp.getType())) {
                                    FTPClient client = new FTPClient();
                                    getFileFromFTP(e, referential, importConfiguration, configurationFtp, client);
                                }

                                if ("SFTP".equals(configurationFtp.getType())) {
                                    getFileFromSFTP(e, referential, importConfiguration, configurationFtp);
                                }
                            }

                            // URL
                            for (ConfigurationUrl configurationUrl : importConfiguration.getConfigurationUrlList()) {
                                getFileFromUrl(e, referential, importConfiguration, configurationUrl);
                            }
                        })
                        .choice()
                            .when(header(WORKLOW).isNotNull())
                                .to("direct:importLaunch")
                        .endChoice()
                    .otherwise()
                        .log("L'import avec l'identifiant ${header." + IMPORT_CONFIGURATION_ID + "} n'est pas actif.")
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

    private void parseImportParameters(Exchange e, ImportConfiguration importConfiguration) {
        ImportParameters importParameters = importConfiguration.getImportParameters().size() > 0 ? importConfiguration.getImportParameters().get(0) : null;
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
            e.getIn().setHeader(WORKLOW, importConfiguration.getWorkflow().toString());
            e.getIn().setHeader(REMOVE_PARENT_STATIONS, importParameters.getRemoveParentStations());
            e.getIn().setHeader(UPDATE_STOP_ACCESSIBILITY, importParameters.getUpdateStopAccessibility());
            e.getIn().setHeader(RAIL_UIC_PROCESSING, importParameters.getRailUICprocessing());
            e.getIn().setHeader(DISTANCE_GEOLOCATION, importParameters.getDistanceGeolocation());
            e.getIn().setHeader(DESCRIPTION, importParameters.getDescription());
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

    private void getFileFromUrl(Exchange e, String referential, ImportConfiguration importConfiguration, ConfigurationUrl configurationUrl) throws Exception {
        if (StringUtils.isNotEmpty(configurationUrl.getLogin()) && configurationUrl.getPassword() != null && configurationUrl.getPassword().length > 0) {
            Authenticator.setDefault(new MyAuthenticator(configurationUrl.getLogin(), cipherEncryption.decrypt(configurationUrl.getPassword())));
        }

        trustAllCertificates();

        URL url = new URL(configurationUrl.getUrl());

        HttpURLConnection httpURLConnection;
        httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.connect();

        if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            if (StringUtils.isNotEmpty(configurationUrl.getUrlInfo())) {
                URL urlInfo = new URL(configurationUrl.getUrlInfo());
                InputStream inputStreamUrlInfo = urlInfo.openStream();
                JSONParser jsonParser = new JSONParser();
                org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) jsonParser.parse(new InputStreamReader(inputStreamUrlInfo, StandardCharsets.UTF_8));
                String stringDateLastModified = jsonObject.get("updated").toString();
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(stringDateLastModified);
                LocalDateTime dateLastModified = offsetDateTime.toLocalDateTime();
                if (configurationUrl.getLastTimestamp() == null || dateLastModified.isAfter(configurationUrl.getLastTimestamp())) {
                    configurationUrl.setLastTimestamp(dateLastModified);
                    uploadFileAndUpdateLastTimestampFromUrl(e, referential, importConfiguration, url);
                } else {
                    log.info("No new file to import for the referential : " + referential + " for the import configuration URL : " + configurationUrl.getUrl());
                    sendMailForFileAlreadyImported(importConfiguration, referential, url.getPath().substring(url.getPath().lastIndexOf('/') + 1));
                }
            } else {
                HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                long date = httpCon.getLastModified();
                if (date != 0) {
                    LocalDateTime dateLastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), TimeZone.getDefault().toZoneId());
                    if (configurationUrl.getLastTimestamp() == null || dateLastModified.isAfter(configurationUrl.getLastTimestamp())) {
                        configurationUrl.setLastTimestamp(dateLastModified);
                        uploadFileAndUpdateLastTimestampFromUrl(e, referential, importConfiguration, url);
                    } else {
                        log.info("No file to import for the dataspace : " + referential + " for the import configuration URL : " + configurationUrl.getUrl());
                        sendMailForFileAlreadyImported(importConfiguration, referential, url.getPath().substring(url.getPath().lastIndexOf('/') + 1));
                    }
                }
                if (date == 0) {
                    configurationUrl.setLastTimestamp(LocalDateTime.now());
                    uploadFileAndUpdateLastTimestampFromUrl(e, referential, importConfiguration, url);
                }
            }

        }else{
            sendMailForFileNotFound(importConfiguration, referential, url.getPath().substring(url.getPath().lastIndexOf('/') + 1));
        }


    }

    private void uploadFileAndUpdateLastTimestampFromUrl(Exchange e, String referential, ImportConfiguration importConfiguration, URL url) throws IOException {
        InputStream inputStream = url.openStream();
        String fileName = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
        setBodyWithFileAndUpdateLastTimestamp(e, referential, importConfiguration, inputStream, fileName);
    }

    private void trustAllCertificates() {
        // Create a new trust manager that trust all certificates
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Activate the new trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception exception) {
            log.error("Certificate management error to download a file from a URL :", exception);
        }
    }

    private void getFileFromSFTP(Exchange e, String referential, ImportConfiguration importConfiguration, ConfigurationFtp configurationFtp) {
        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(configurationFtp.getLogin(), configurationFtp.getUrl(), Math.toIntExact(configurationFtp.getPort()));
            session.setPassword(cipherEncryption.decrypt(configurationFtp.getPassword()));
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel("sftp");
            channel.connect();
            log.info("SFTP channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            if (StringUtils.isNotEmpty(configurationFtp.getFolder())) {
                channelSftp.cd(configurationFtp.getFolder());
            }

            InputStream inputStream = channelSftp.get(configurationFtp.getFilename());
            LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(channelSftp.stat(configurationFtp.getFilename()).getMTime()), TimeZone.getDefault().toZoneId());

            File targetFile = new File(configurationFtp.getFilename());

            FileUtils.copyInputStreamToFile(inputStream, targetFile);

            if (targetFile.length() > 0) {
                if (configurationFtp.getLastTimestamp() == null || localDateTime.isAfter(configurationFtp.getLastTimestamp())) {
                    configurationFtp.setLastTimestamp(localDateTime);
                    setBodyWithFileAndUpdateLastTimestamp(e, referential, importConfiguration, new FileInputStream(targetFile), configurationFtp.getFilename());
                } else {
                    log.info("No new file to import for the dataspace : " + referential + " for the import configuration SFTP : " + configurationFtp.getUrl());
                    sendMailForFileAlreadyImported(importConfiguration, referential, configurationFtp.getFilename());
                }
            } else {
                log.info("File " + configurationFtp.getFilename() + " not founded for the dataspace " + referential);
                sendMailForFileNotFound(importConfiguration, referential, configurationFtp.getFilename());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("Exception found while transfer the response.", ex.getMessage());
            if (ex.getMessage().equals("No such file")){
                sendMailForFileNotFound(importConfiguration, referential, configurationFtp.getFilename());
            }
        } finally {
            channelSftp.exit();
            log.info("SFTP Channel exited.");
            channel.disconnect();
            log.info("Channel disconnected.");
            session.disconnect();
            log.info("Host Session disconnected.");
        }
    }

    private void getFileFromFTP(Exchange e, String referential, ImportConfiguration importConfiguration, ConfigurationFtp configurationFtp, FTPClient client) throws Exception {
        FTPClientConfig ftpClientConfig = new FTPClientConfig();
        client.configure(ftpClientConfig);
        client.connect(configurationFtp.getUrl(), Math.toIntExact(configurationFtp.getPort()));
        if (StringUtils.isNotEmpty(configurationFtp.getLogin()) && configurationFtp.getPassword() != null && configurationFtp.getPassword().length > 0) {
            client.login(configurationFtp.getLogin(), cipherEncryption.decrypt(configurationFtp.getPassword()));
            client.enterLocalPassiveMode();

            FTPFile[] files = client.listFiles(configurationFtp.getFolder());
            Optional<FTPFile> optionalFTPFile = Arrays.stream(files).filter(ftpFile -> ftpFile.getName().equals(configurationFtp.getFilename())).findFirst();

            if (optionalFTPFile.isPresent()) {
                FTPFile file = optionalFTPFile.get();
                LocalDateTime localDateTime = LocalDateTime.ofInstant(file.getTimestamp().toInstant(), file.getTimestamp().getTimeZone().toZoneId());
                if (configurationFtp.getLastTimestamp() == null || localDateTime.isAfter(configurationFtp.getLastTimestamp())) {
                    configurationFtp.setLastTimestamp(localDateTime);
                    setBodyWithFileAndUpdateLastTimestamp(e, referential, importConfiguration, client.retrieveFileStream(configurationFtp.getFolder() + "/" + file.getName()), file.getName());
                } else {
                    log.info("No new file to import for the dataspace : " + referential + " for the import configuration FTP : " + configurationFtp.getUrl());
                    sendMailForFileAlreadyImported(importConfiguration, referential, configurationFtp.getFilename());
                }
            } else {
                log.info("File " + configurationFtp.getFilename() + " not founded for the dataspace " + referential);
                sendMailForFileNotFound(importConfiguration, referential, configurationFtp.getFilename());
            }
        }
    }

    /**
     * Send a mail to warn that file was not found
     * @param importConfiguration
     *    the configuration of the automatic import
     * @param referential
     *    the referential for which file was not found
     * @param filename
     *    the file name that was not found
     */
    private void sendMailForFileNotFound(ImportConfiguration importConfiguration,String referential, String filename) {

        String mailObject = "MOBIITI - import automatique - Fichier non trouve";
        LocalDate now= LocalDate.now();
        String text  = "Bonjour, <br> Après vérification, il n'y pas de nouvelle offre à intégrer pour la date du " + now.toString() + ". <br>" +
                        "Nom du fichier : " + filename + " <br>" +
                        "Organisation : " + referential +  " <br>" +
                        "Cordialement,<br> L'équipe Mobi-iti";


        for (Recipient recipient : importConfiguration.getRecipients()) {
            sendMail.sendEmail(mailObject, recipient.getEmail(), text, null);
        }

    }

    /**
     * Send a mail to warn that file was already imported
     * @param importConfiguration
     *    the configuration of the automatic import
     * @param referential
     *    the referential for which file was already imported
     * @param filename
     *    the file name that was already imported
     */
    private void sendMailForFileAlreadyImported(ImportConfiguration importConfiguration,String referential, String filename) {

        String mailObject = "MOBIITI - import automatique - Fichier deja importe";
        LocalDate now= LocalDate.now();
        String text  = "Bonjour, <br> Après vérification, il n'y pas de nouvelle offre à intégrer pour la date du " + now.toString() +
                ", le fichier ayant déjà été importé précédemment. <br>" +
                "Nom du fichier : " + filename + " <br>" +
                "Organisation : " + referential +  " <br>" +
                "Cordialement,<br> L'équipe Mobi-iti";


        for (Recipient recipient : importConfiguration.getRecipients()) {
            sendMail.sendEmail(mailObject, recipient.getEmail(), text, null);
        }

    }

    private void setBodyWithFileAndUpdateLastTimestamp(Exchange e, String referential, ImportConfiguration importConfiguration, InputStream inputStream, String fileName) throws IOException {
        fileName = StringUtils.appendIfMissing(fileName, ".zip");
        java.io.File file = new File(fileName);
        FileItemFactory fac = new DiskFileItemFactory();
        FileItem fileItem = fac.createItem("file", "application/zip", false, file.getName());
        Streams.copy(inputStream, fileItem.getOutputStream(), true);
        e.getIn().setBody(fileItem);

        //Update last timestamp
        importConfigurationDAO.update(referential, importConfiguration);
        log.info("Import configuration of " + referential + " updated");

        // Parse import parameters from import configuration
        parseImportParameters(e, importConfiguration);
    }

    private void getCron(Exchange e) throws SchedulerException {
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
            if (headers.get(IMPORT_CONFIGURATION_SCHEDULER) != null &&
                    provider.chouetteInfo.referential != null &&
                    headers.get(IMPORT_CONFIGURATION_ID) != null) {
                SchedulerFactoryBean scheduler = schedulerImportConfiguration.getSchedulerImportConfiguration();

                String importConfigurationSchedulerCron = (String) headers.get(IMPORT_CONFIGURATION_SCHEDULER);
                Integer importConfigurationId = (Integer) headers.get(IMPORT_CONFIGURATION_ID);

                if (StringUtils.isNotEmpty(importConfigurationSchedulerCron)) {
                    String[] dateFromCron = importConfigurationSchedulerCron.split(" ");

                    Date startDate = getStartDate();

                    importConfigurationSchedulerCron = dateFromCron[0] + " " + dateFromCron[1] + " " + dateFromCron[2] + " ? * " + dateFromCron[5] + " " + dateFromCron[6];

                    JobDetail importConfigurationJobDetails = JobBuilder.newJob(ImportConfigurationJob.class)
                            .withIdentity("ImportConfigurationJobDetails-" + provider.chouetteInfo.referential + "-" + importConfigurationId)
                            .storeDurably(true)
                            .build();

                    Trigger importConfigurationTrigger = TriggerBuilder.newTrigger()
                            .forJob(importConfigurationJobDetails)
                            .withIdentity("ImportConfigurationJobTrigger-" + provider.chouetteInfo.referential + "-" + importConfigurationId)
                            .withSchedule(CronScheduleBuilder.cronSchedule(importConfigurationSchedulerCron).withMisfireHandlingInstructionDoNothing())
                            .startAt(startDate)
                            .build();

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
        JobDetail importConfigurationJobDetails = JobBuilder.newJob(ImportConfigurationJob.class)
                .withIdentity("ImportConfigurationJobDetails-" + provider.chouetteInfo.referential + "-" + importConfigurationId)
                .build();
        if (scheduler.getScheduler().checkExists(importConfigurationJobDetails.getKey())) {
            scheduler.getScheduler().deleteJob(importConfigurationJobDetails.getKey());
            log.info("Import Configuration Scheduler for " + provider.chouetteInfo.referential + "-" + importConfigurationId + " deleted");
        } else {
            log.info("Import Configuration Scheduler for " + provider.chouetteInfo.referential + "-" + importConfigurationId + " not deleted because not existed");
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
     * Calculate the start date of the cron : 1st day of the current month
     *
     * @return
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

