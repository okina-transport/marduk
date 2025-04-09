package no.rutebanken.marduk.routes.pigma;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import no.rutebanken.marduk.Utils.SendMail;
import no.rutebanken.marduk.Utils.SlackNotification;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.services.BlobStoreService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.marduk.Constants.MERGED_NETEX_STOPS_ROOT_DIR;


@Component
public class SftpPigmaExportRouteBuilder extends BaseRouteBuilder {


    @Value("${upload.pigma.cron}")
    private String uploadPigmaCron;

    @Value("${sftp.pigma.host}")
    private String sftpHost;

    @Value("${sftp.pigma.login:do-not-commit-sensitive-data}")
    private String sftpUser;

    @Value("${sftp.pigma.port}")
    private Integer sftpPort;

    @Value("${sftp.pigma.protocol}")
    private String sftpProtocol;

    @Value("${sftp.pigma.privateKey}")
    private String sftpPrivateKey;

    @Value("${sftp.pigma.passphrase:do-not-commit-sensitive-data}")
    private String sftpPassphrase;

    @Value("${sftp.pigma.path}")
    private String sftpPath;

    @Autowired
    private SlackNotification slackNotification;

    @Autowired
    private MetadataFile metadataFile;

    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    private SendMail sendMail;

    @Value("${pigma.upload.path}")
    private String pigmaUploadPath;


    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/uploadPigma?cron=" + uploadPigmaCron)
                .process(e -> sendFilesToPigmaPlatform())
                .routeId("uploadPigma");

    }
    public void sendFilesToPigmaPlatform() {

        ArrayList<BlobStoreFiles.File> listBlobStoreFiles = new ArrayList<>();
        ArrayList<File> files = new ArrayList<>();

        // Netex and GTFS files
        BlobStoreFiles blobStoreFiles = blobStoreService.listBlobsInFolders(pigmaUploadPath);
        if(!blobStoreFiles.getFiles().isEmpty()){
            List<BlobStoreFiles.File> netexAndGtfsfiles = blobStoreFiles.getFiles();
            listBlobStoreFiles.addAll(netexAndGtfsfiles);
        }

        //Netex file stops
        BlobStoreFiles netexFileStops = blobStoreService.listBlobsInFolders(MERGED_NETEX_STOPS_ROOT_DIR + "/CurrentAndFuture_latest.zip");
        if(!netexFileStops.getFiles().isEmpty()){
            listBlobStoreFiles.add(netexFileStops.getFiles().get(0));
        }


        //Create a metadata file for each file
        files.add(metadataFile.createMetadataFile("naq-metadonnes.csv", listBlobStoreFiles));

        for (BlobStoreFiles.File file : listBlobStoreFiles) {
            try (InputStream inputStream = blobStoreService.getBlob(file.getName().replaceFirst(pigmaUploadPath + "/", ""))) {

                File zipFile;
                switch (file.getFileNameOnly()) {
                    case "CurrentAndFuture_latest.zip" :
                        zipFile = new File("naq-stops-netex.zip");
                        break;
                    case "mobiiti_technique-aggregated-netex.zip" :
                        zipFile = new File("naq-aggregated-netex.zip");
                        break;
                    case "mobiiti_technique-aggregated-gtfs.zip" :
                        zipFile = new File("naq-aggregated-gtfs.zip");
                        break;
                    default :
                        zipFile = new File(file.getFileNameOnly().replace("mobiiti_", ""));
                        break;
                }

                FileUtils.copyInputStreamToFile(inputStream, zipFile);
                files.add(zipFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        uploadFiles(files);
    }



    public void uploadFiles(ArrayList<File> files){
        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(sftpPrivateKey, sftpPassphrase);
            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            log.info("Host connected.");
            channel = session.openChannel(sftpProtocol);
            channel.connect();
            log.info("SFTP channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(sftpPath);
            for (File file : files) {
                try (FileInputStream src = new FileInputStream(file.getName())) {
                    channelSftp.put(src, file.getName(), ChannelSftp.OVERWRITE);
                    log.info("File transfered : {} successfully to host.", file.getName());
                }
            }
            log.info("All files transfered successfully to host.");
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("Exception found while transfer the response. {}", ex.getMessage());
            slackNotification.sendSlackNotificationTitleAndMessage(SlackNotification.NOTIFICATION_CHANNEL, "Erreur upload des fichiers sur la plateforme Pigma", "Les fichiers n'ont pas pu être exportés sur la plateforme Pigma.");
            sendMail.sendEmail("Erreur upload des fichiers sur la plateforme Pigma", "developer@okina.fr", "Les fichiers n'ont pas pu être exportés sur la plateforme Pigma.", null);

        } finally {
            channelSftp.exit();
            log.info("SFTP Channel exited.");
            channel.disconnect();
            log.info("Channel disconnected.");
            session.disconnect();
            log.info("Host Session disconnected.");
        }
    }
}
