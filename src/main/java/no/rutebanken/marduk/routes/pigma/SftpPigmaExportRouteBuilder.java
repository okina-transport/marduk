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

import static no.rutebanken.marduk.Constants.EXPORT_GLOBAL_GTFS_ZIP;
import static no.rutebanken.marduk.Constants.EXPORT_GLOBAL_NETEX_ZIP;
import static no.rutebanken.marduk.Constants.MERGED_NETEX_ROOT_DIR;
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

    @Value("${chouette.storage.path:/srv/docker-data/data/chouette}")
    private String chouetteStoragePath;


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

        // Netex files
        BlobStoreFiles netexBlobStoreFiles = blobStoreService.listBlobsInFolders(MERGED_NETEX_ROOT_DIR + "/");
        if(netexBlobStoreFiles.getFiles().size() != 0){
            List<BlobStoreFiles.File> netexFiles = netexBlobStoreFiles.getFiles();
            listBlobStoreFiles.addAll(netexFiles);
        }

        // GTFS files
        BlobStoreFiles gtfsBlobStoreFiles = blobStoreService.listBlobsInFolders("mobiiti_technique/gtfs/allFiles/TRIDENT/");
        if(gtfsBlobStoreFiles.getFiles().size() != 0){
            List<BlobStoreFiles.File> gtfsFiles = gtfsBlobStoreFiles.getFiles();
            listBlobStoreFiles.addAll(gtfsFiles);
        }

        // Aggregated Netex file
        BlobStoreFiles aggregatedNetexFileBlobStoreFiles = blobStoreService.listBlobsInFolders("mobiiti_technique/netex/" + EXPORT_GLOBAL_NETEX_ZIP);
        if(aggregatedNetexFileBlobStoreFiles.getFiles().size() != 0){
            listBlobStoreFiles.add(aggregatedNetexFileBlobStoreFiles.getFiles().get(0));
        }

        // Aggregated GTFS file
        BlobStoreFiles aggregatedGtfsFileBlobStoreFiles = blobStoreService.listBlobsInFolders("mobiiti_technique/gtfs/TRIDENT/" + EXPORT_GLOBAL_GTFS_ZIP);
        if(aggregatedGtfsFileBlobStoreFiles.getFiles().size() != 0){
            listBlobStoreFiles.add(aggregatedGtfsFileBlobStoreFiles.getFiles().get(0));
        }

        //Netex file stops
        BlobStoreFiles netexFileStops = blobStoreService.listBlobsInFolders(MERGED_NETEX_STOPS_ROOT_DIR + "/CurrentAndFuture_latest.zip");
        if(netexFileStops.getFiles().size() != 0){
            listBlobStoreFiles.add(netexFileStops.getFiles().get(0));
        }


        //Create a metadata file for each file
        files.add(metadataFile.createMetadataFile("naq-metadonnes.csv", listBlobStoreFiles));

        for (BlobStoreFiles.File file : listBlobStoreFiles) {
            try (InputStream inputStream = blobStoreService.getBlob(file.getName().replaceFirst(chouetteStoragePath + "/", ""))) {

                File zipFile;
                switch (file.getFileNameOnly()) {
                    case "CurrentAndFuture_latest.zip" :
                        zipFile = new File("naq-stops-netex.zip");
                        break;
                    case EXPORT_GLOBAL_NETEX_ZIP :
                        zipFile = new File("naq-aggregated-netex.zip");
                        break;
                    case EXPORT_GLOBAL_GTFS_ZIP :
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
                    log.info("File transfered : " + file.getName() + " successfully to host.");
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
