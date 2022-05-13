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
import org.springframework.beans.factory.annotation.Value;
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
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_CONFIGURATION_SCHEDULER;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

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

    @Value("${marduk.upload.import.configuration.path}")
    private String importConfigurationPath;

    @Value("${marduk.upload.public.path:/tmp}")
    private String publicUploadPath;

    @Value("${app.url}")
    private String appUrl;

    @Value("${client.name}")
    private String client;

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
                    ImportConfiguration importConfiguration = importConfigurationDAO.getImportConfiguration(referential);
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");

                    // FTP
                    for (ConfigurationFtp configurationFtp : importConfiguration.getConfigurationFtpList()) {
                        if("FTP".equals(configurationFtp.getType())){
                            FTPClient client = new FTPClient();
                            getFileFromFTP(e, referential, importConfiguration, formatter, configurationFtp, client);
                        }

                        if("SFTP".equals(configurationFtp.getType())){
                            getFileFromSFTP(e, referential, importConfiguration, formatter, configurationFtp);
                        }
                    }

                    // URL
                    for (ConfigurationUrl configurationUrl : importConfiguration.getConfigurationUrlList()) {
                        getFileFromUrl(e, referential, importConfiguration, formatter, configurationUrl);
                    }
                })
                .routeId("import-configuration-job");


        from("direct:updateSchedulerForImportConfiguration")
                .log(LoggingLevel.INFO, getClass().getName(), "Update scheduler Import configuration")
                .process(this::updateSchedulerForImportConfiguration)
                .routeId("update-scheduler-process-import-configuration");

        from("direct:getCron")
                .log(LoggingLevel.INFO, getClass().getName(), "Get scheduler Import Configuration")
                .process(this::getCron)
                .routeId("get-cron-scheduler-process-import-configuration");
    }

    private void getFileFromUrl(Exchange e, String referential, ImportConfiguration importConfiguration, SimpleDateFormat formatter, ConfigurationUrl configurationUrl) throws Exception {
        if (StringUtils.isNotEmpty(configurationUrl.getLogin()) && configurationUrl.getPassword() != null && configurationUrl.getPassword().length > 0) {
            Authenticator.setDefault(new MyAuthenticator(configurationUrl.getLogin(), cipherEncryption.decrypt(configurationUrl.getPassword())));
        }

        trustAllCertificates();

        URL url = new URL(configurationUrl.getUrl());
        if (url.openStream().available() > 0) {
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
                    uploadFileAndUpdateLastTimestampFromUrl(e, referential, importConfiguration, formatter, configurationUrl, url);
                }
                else{
                    log.info("No new file to import for the referential : " + referential + " for the import configuration URL : " + configurationUrl.getUrl());
                }
            } else {
                HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                long date = httpCon.getLastModified();
                if (date != 0) {
                    LocalDateTime dateLastModified = LocalDateTime.ofInstant(Instant.ofEpochSecond(date), TimeZone.getDefault().toZoneId());
                    if (configurationUrl.getLastTimestamp() == null || dateLastModified.isAfter(configurationUrl.getLastTimestamp())) {
                        configurationUrl.setLastTimestamp(dateLastModified);
                        uploadFileAndUpdateLastTimestampFromUrl(e, referential, importConfiguration, formatter, configurationUrl, url);
                    }
                    else{
                        log.info("No file to import for the dataspace : " + referential + " for the import configuration URL : " + configurationUrl.getUrl());
                    }
                }
                if (date == 0) {
                    configurationUrl.setLastTimestamp(LocalDateTime.now());
                    uploadFileAndUpdateLastTimestampFromUrl(e, referential, importConfiguration, formatter, configurationUrl, url);
                }
                else{
                    log.info("No file to import for the dataspace : " + referential + " for the import configuration URL : " + configurationUrl.getUrl());
                }
            }
        }
    }

    private void uploadFileAndUpdateLastTimestampFromUrl(Exchange e, String referential, ImportConfiguration importConfiguration, SimpleDateFormat formatter, ConfigurationUrl configurationUrl, URL url) throws IOException {
        InputStream inputStream = url.openStream();
        String fileName = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
        Date dateDownloadedFile = Date.from(Instant.now());
        String destinationPath = importConfigurationPath + "/" + publicUploadPath  + "/" + referential + "/for_import/" + formatter.format(dateDownloadedFile) + "-" + formatter.format(Date.from(configurationUrl.getLastTimestamp().atZone(ZoneId.systemDefault()).toInstant())) + "/" + fileName;
        uploadFileAndUpdateLastTimestamp(e, referential, importConfiguration, inputStream, fileName, destinationPath, dateDownloadedFile);
        sendMail(importConfiguration.getRecipients(), referential, fileName, appUrl + "/" + referential + "/for_import/" + formatter.format(dateDownloadedFile) + "-" + formatter.format(Date.from(configurationUrl.getLastTimestamp().atZone(ZoneId.systemDefault()).toInstant())) + "/" + fileName);
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

    private void getFileFromSFTP(Exchange e, String referential, ImportConfiguration importConfiguration, SimpleDateFormat formatter, ConfigurationFtp configurationFtp) {
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
                    Date dateDownloadedFile = Date.from(Instant.now());
                    String destinationPath = importConfigurationPath + "/" + publicUploadPath  + "/" + referential + "/for_import/" + formatter.format(dateDownloadedFile) + "-" + formatter.format(Date.from(configurationFtp.getLastTimestamp().atZone(ZoneId.systemDefault()).toInstant())) + "/" + configurationFtp.getFilename();
                    uploadFileAndUpdateLastTimestamp(e, referential, importConfiguration, new FileInputStream(targetFile), configurationFtp.getFilename(), destinationPath, dateDownloadedFile);
                    sendMail(importConfiguration.getRecipients(), referential, configurationFtp.getFilename(), appUrl + "/" + referential + "/for_import/" + formatter.format(dateDownloadedFile) + "-" + formatter.format(Date.from(configurationFtp.getLastTimestamp().atZone(ZoneId.systemDefault()).toInstant())) + "/" + configurationFtp.getFilename());
                }
                else{
                    log.info("No new file to import for the dataspace : " + referential + " for the import configuration SFTP : " + configurationFtp.getUrl());
                }
            }
            else {
                log.info("File " + configurationFtp.getFilename() + " not founded for the dataspace " + referential);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("Exception found while transfer the response.", ex.getMessage());
        } finally {
            channelSftp.exit();
            log.info("SFTP Channel exited.");
            channel.disconnect();
            log.info("Channel disconnected.");
            session.disconnect();
            log.info("Host Session disconnected.");
        }
    }

    private void getFileFromFTP(Exchange e, String referential, ImportConfiguration importConfiguration, SimpleDateFormat formatter, ConfigurationFtp configurationFtp, FTPClient client) throws Exception {
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
                    Date dateDownloadedFile = Date.from(Instant.now());
                    String destinationPath = importConfigurationPath + "/" + publicUploadPath  + "/" + referential + "/for_import/" + formatter.format(dateDownloadedFile) + "-" + formatter.format(Date.from(configurationFtp.getLastTimestamp().atZone(ZoneId.systemDefault()).toInstant())) + "/" + configurationFtp.getFilename();
                    uploadFileAndUpdateLastTimestamp(e, referential, importConfiguration, client.retrieveFileStream(configurationFtp.getFolder() + "/" + file.getName()), file.getName(), destinationPath, dateDownloadedFile);
                    sendMail(importConfiguration.getRecipients(), referential, file.getName(), appUrl + "/" + referential + "/for_import/" + formatter.format(dateDownloadedFile) + "-" + formatter.format(Date.from(configurationFtp.getLastTimestamp().atZone(ZoneId.systemDefault()).toInstant())) + "/" + configurationFtp.getFilename());
                }
                else{
                    log.info("No new file to import for the dataspace : " + referential + " for the import configuration FTP : " + configurationFtp.getUrl());
                }
            } else {
                log.info("File " + configurationFtp.getFilename() + " not founded for the dataspace " + referential);
            }
        }
    }

    private void uploadFileAndUpdateLastTimestamp(Exchange e, String referential, ImportConfiguration importConfiguration, InputStream inputStream, String fileName, String destinationPath, Date dateDownloadedFile) throws IOException {
        //Upload file on filesystem
        e.getIn().setHeader(FILE_HANDLE, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).mobiitiId + "/imports/" + e.getIn().getBody(String.class));
        blobStoreService.uploadBlob(destinationPath, true, inputStream);
        log.info("File : " + fileName + " uploaded on " + destinationPath);

        //Update last timestamp
        importConfigurationDAO.update(referential, importConfiguration);
        log.info("Import configuration of " + referential + " updated");
    }

    private void getCron(Exchange e) throws SchedulerException {
        SchedulerFactoryBean scheduler = schedulerImportConfiguration.getSchedulerImportConfiguration();
        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        CronTrigger trigger = (CronTrigger) scheduler.getScheduler().getTrigger(TriggerKey.triggerKey("ImportConfigurationJobTrigger-" + provider.chouetteInfo.referential));
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

    private void updateSchedulerForImportConfiguration(Exchange e) throws SchedulerException {
        Map headers = (Map) e.getIn().getBody(Map.class).get("headers");
        Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        if (headers != null) {
            if (headers.get(IMPORT_CONFIGURATION_SCHEDULER) != null && provider.chouetteInfo.referential != null) {
                SchedulerFactoryBean scheduler = schedulerImportConfiguration.getSchedulerImportConfiguration();

                String importConfigurationSchedulerCron = (String) headers.get(IMPORT_CONFIGURATION_SCHEDULER);

                if(StringUtils.isNotEmpty(importConfigurationSchedulerCron)){
                    String[] dateFromCron = importConfigurationSchedulerCron.split(" ");

                    Date startDate = getStartDate();

                    importConfigurationSchedulerCron = dateFromCron[0] + " " + dateFromCron[1] + " " + dateFromCron[2] + " ? * " + dateFromCron[5] + " " + dateFromCron[6];

                    JobDetail importConfigurationJobDetails = JobBuilder.newJob(ImportConfigurationJob.class)
                            .withIdentity("ImportConfigurationJobDetails-" + provider.chouetteInfo.referential)
                            .storeDurably(true)
                            .build();

                    Trigger importConfigurationTrigger = TriggerBuilder.newTrigger()
                            .forJob(importConfigurationJobDetails)
                            .withIdentity("ImportConfigurationJobTrigger-" + provider.chouetteInfo.referential)
                            .withSchedule(CronScheduleBuilder.cronSchedule(importConfigurationSchedulerCron).withMisfireHandlingInstructionDoNothing())
                            .startAt(startDate)
                            .build();

                    scheduler.start();

                    if (scheduler.getScheduler().checkExists(importConfigurationJobDetails.getKey())) {
                        scheduler.getScheduler().deleteJob(importConfigurationJobDetails.getKey());
                    }

                    scheduler.getScheduler().scheduleJob(importConfigurationJobDetails, importConfigurationTrigger);

                    log.info("Import Configuration Scheduler for " + provider.chouetteInfo.referential + " created with cron expression: " + importConfigurationSchedulerCron);
                }
                else {
                    JobDetail importConfigurationJobDetails = JobBuilder.newJob(ImportConfigurationJob.class)
                            .withIdentity("ImportConfigurationJobDetails-" + provider.chouetteInfo.referential)
                            .build();
                    if (scheduler.getScheduler().checkExists(importConfigurationJobDetails.getKey())) {
                        scheduler.getScheduler().deleteJob(importConfigurationJobDetails.getKey());
                        log.info("Import Configuration Scheduler for " + provider.chouetteInfo.referential + " deleted");
                    }
                    else{
                        log.info("Import Configuration Scheduler for " + provider.chouetteInfo.referential + " not deleted because not existed");
                    }
                }
            }
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

    private void sendMail(List<Recipient> recipients, String referential, String fileName, String destinationPath) {
        for (Recipient recipient : recipients) {
            if (StringUtils.isNotEmpty(recipient.getEmail())) {
                sendMail.sendEmail(client.toUpperCase() + " Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                        recipient.getEmail(),
                        "Bonjour,"
                                + "\nUn nouveau fichier de données est disponible pour intégration dans le Référentiel Mobi-iti. Voici le lien de téléchargement : "
                                + "\n<a target=\"_blank\" href=\"" + destinationPath + "\">" + fileName + "</a>",
                        null);
            }
        }
    }
}

