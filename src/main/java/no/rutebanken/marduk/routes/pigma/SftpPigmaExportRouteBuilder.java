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


@Component
public class SftpPigmaExportRouteBuilder extends BaseRouteBuilder {


    @Value("${upload.pigma.cron}")
    private String uploadPigmaCron;

    @Value("${sftp.pigma.host}")
    private String sftpHost;

    @Value("${sftp.pigma.login}")
    private String sftpUser;

    @Value("${sftp.pigma.port}")
    private Integer sftpPort;

    @Value("${sftp.pigma.protocol}")
    private String sftpProtocol;

    @Value("${sftp.pigma.privateKey}")
    private String sftpPrivateKey;

    @Value("${sftp.pigma.passphrase}")
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


    @Override
    public void configure() throws Exception {
        super.configure();

        //TODO activer le cron en activant le singleton ci dessous, en poussant les properties de marduk prod et activer le sftp channel
//        from("direct:uploadPigma")
        singletonFrom("quartz2://marduk/uploadPigma?cron=" + uploadPigmaCron)
                .process(e -> sendFilesToPigmaPlatform())
                .routeId("uploadPigma");

    }
    public void sendFilesToPigmaPlatform() {

        ArrayList<File> metadataFiles = new ArrayList<>();
        ArrayList<BlobStoreFiles.File> listBlobStoreFiles = new ArrayList<>();
        ArrayList<File> files = new ArrayList<>();

        // Netex files
        BlobStoreFiles netexBlobStoreFiles = blobStoreService.listBlobsInFolders("outbound/netex/");
        if(netexBlobStoreFiles.getFiles().size() != 0){
            List<BlobStoreFiles.File> netexFiles = netexBlobStoreFiles.getFiles();
            listBlobStoreFiles.addAll(netexFiles);
        }

        // GTFS files
        BlobStoreFiles gtfsBlobStoreFiles = blobStoreService.listBlobsInFolders("outbound/gtfs/");
        if(gtfsBlobStoreFiles.getFiles().size() != 0){
            List<BlobStoreFiles.File> gtfsFiles = gtfsBlobStoreFiles.getFiles();
            listBlobStoreFiles.addAll(gtfsFiles);
        }

        // Aggregated Netex file
        BlobStoreFiles aggregatedNetexFileBlobStoreFiles = blobStoreService.listBlobsInFolders("outbound/naq-aggregated-netex.zip");
        if(aggregatedNetexFileBlobStoreFiles.getFiles().size() != 0){
            listBlobStoreFiles.add(aggregatedNetexFileBlobStoreFiles.getFiles().get(0));
        }

        // Aggregated GTFS file
        BlobStoreFiles aggregatedGtfsFileBlobStoreFiles = blobStoreService.listBlobsInFolders("outbound/naq-aggregated-gtfs.zip");
        if(aggregatedGtfsFileBlobStoreFiles.getFiles().size() != 0){
            listBlobStoreFiles.add(aggregatedGtfsFileBlobStoreFiles.getFiles().get(0));
        }

        //Netex file stops
        BlobStoreFiles netexFileStops = blobStoreService.listBlobsInFolders("tiamat/CurrentAndFuture_latest.zip");
        if(netexFileStops.getFiles().size() != 0){
            listBlobStoreFiles.add(netexFileStops.getFiles().get(0));
        }


        //Create a metadata file for each file
        files.add(metadataFile.createMetadataFile("naq-metadonnes.csv", listBlobStoreFiles));

        for (BlobStoreFiles.File file : listBlobStoreFiles) {
            try (InputStream inputStream = blobStoreService.getBlob(file.getName())) {

                File zipFile;
                if (file.getFileNameOnly().equals("CurrentAndFuture_latest.zip")) {
                    zipFile = new File("naq-stops-netex.zip");
                } else {
                    zipFile = new File(file.getFileNameOnly());
                }

                FileUtils.copyInputStreamToFile(inputStream, zipFile);
                files.add(zipFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        ArrayList<File> filesMail = new ArrayList<>();
//        for(File fileMail : files){
//            if(fileMail.getName().equals("naq-metadonnes.csv")){
//                filesMail.add(fileMail);
//            }
//        }
//
//        sendMail.sendEmail("Test", "Envoie de fichiers test", filesMail);

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
                }
            }
            log.info("File transfered successfully to host.");
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("Exception found while transfer the response.", ex.getMessage());
            slackNotification.sendSlackNotificationTitleAndMessage(SlackNotification.NOTIFICATION_CHANNEL, "Erreur upload des fichiers sur la plateforme Pigma", "Les fichiers n'ont pas pu être exportés sur la plateforme Pigma.");
            sendMail.sendEmail("Erreur upload des fichiers sur la plateforme Pigma", "Les fichiers n'ont pas pu être exportés sur la plateforme Pigma.", null);

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
