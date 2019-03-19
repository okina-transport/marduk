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
    private CreateMetadataFile createMetadataFile;

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
        for(BlobStoreFiles.File blobStoreFile : listBlobStoreFiles){
            getBlobFileAndCreateMetadataFile(metadataFiles, blobStoreFile);
        }

        files.addAll(metadataFiles);

        for(BlobStoreFiles.File file : listBlobStoreFiles){
            InputStream inputStream = blobStoreService.getBlob(file.getName());
            File zipFile = null;
            if(file.getFileNameOnly().equals("CurrentAndFuture_latest.zip")){
                zipFile = new File("naq-stops-netex.zip");
            }
            else{
                zipFile = new File(file.getFileNameOnly());
            }

            try {
                FileUtils.copyInputStreamToFile(inputStream, zipFile);
                files.add(zipFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        ArrayList<File> filesMail = new ArrayList<>();
//        for(File fileMail : files){
//            if(fileMail.getName().equals("naq-stops-netex-metadonnes.csv")){
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
            jsch.addIdentity("-----BEGIN RSA PRIVATE KEY-----\n" +
                    "Proc-Type: 4,ENCRYPTED\n" +
                    "DEK-Info: AES-128-CBC,05F7C291BD28485FBCA96E237F8D86EF\n" +
                    "\n" +
                    "A9T8uXPCgDbwWOrAPV+wrcwc0ZzoJJM6Cxp0T36OJB8nwj3eowafU9oFn6wXwxNV\n" +
                    "0EfEuy1UuwFoBVqkdyekDDwxbd92MRiO7P+qpgiNx9MDUjXBVRWQRWLYy4fEYOTY\n" +
                    "x7/Ovv/Xvm0zDFKPAaK9PX9ykndRd8KOdxE1rP4Zugr+9kLHGrOJpgYw68MXyJhR\n" +
                    "+BN7mUf4J50tnA4UPt57F9iqM60qQ8XKAOI5Pfv2LXJpM2OigR4fTwJIC984M6JH\n" +
                    "EMgDs6V8ZnjaypTekZLNmuTdFbHsugIGjgOyDixl199naw4VxS3ibofvFWAzqMfV\n" +
                    "UJOB+cRLRcmUkQ4XG0C5CRrpT8KU2IPnWxd1JTWYC7LNA1RvLr4hYllqPKQ2pw1r\n" +
                    "RejmFvfc7nG5Z4FD83//gbzp/GLjMeG91Zr+eOLPNQINaFBUi7gT/TXyD4uTb4IK\n" +
                    "dVG+oKtjDpRA3pWfa34IbhxG2zKG9RFkRrRW93RtYHG0dyTqys75c/Npt9BlKqeD\n" +
                    "YnhN8VUb2zRvbX9KTCj2OB25TMI+9pX4ONW/3HfRona9ZgkUzP1DO64seY02Tboo\n" +
                    "L276JhQRkbwApl6dCz7SMiP2PF8Lk1iW2GGlgiYevkJSh4abpExWxEfFp+Df5tIK\n" +
                    "29RkTgpoVUdnOWTYKvBIYqOWUVrAjwb6rVYusqHM/fe8DbzRALQ1hdNlLsnNI03F\n" +
                    "ZyqTA1GxgIIF2zOBmRubQrZCvyltzJGvPueQpyHbEh0JmdS4DY8uoUZv2ebH3GyA\n" +
                    "MdYpGKZcpj/Yhhu+WhB5t6/6as5Z2DfTp7J78WF4Hqx6vzW0vcTTw3cMWo81EDvq\n" +
                    "UTufJL+xclNZh6t8CV9xhZ0KDpKXfkoMmIEzLUH3f8l09DgllwAIujhbMD8NyIYw\n" +
                    "cR1eJLB0yZME/qqqfAZ46JAwplRdGG5Torr9AdYfWQmmWDPIB0mZ4iTIw26kAXEa\n" +
                    "fZE8YRTa+cUauXeT+kWWOzlOhk8qFy1QCNcSwiEivsucvZ6rPZ4T5PdnrQ74VWxx\n" +
                    "PtL/mp55YmvJOuHPDDXG6320Ti7r2sVadg/oVBiIAo2PvYlRVx3S5KG2hisbZews\n" +
                    "JROvK/5HrRmLNxHQnX6YIyUzw4RT8wPbq/7NBjuiDINOLDUXlkIPkp6ABD5eCwZq\n" +
                    "iJ9JbVRmMsDjnvjK/5f1fhcHgqFN7hWsBKYslqsB5gsmGGeKJfwx+fMi4xoPt4/u\n" +
                    "aqidYJd5+tFS21pUWK5XuMOr22jglyjrNuMRm7l/blZySnDgB14VJ+E21YP60hTk\n" +
                    "HyQGhIRdPSaqJea1H2bq7bG8/1Y63bklMGXX1B01L4S7eMrgmbQdihoR20oeTo20\n" +
                    "UMcVzg7E7ZttHnbk6q+5sJg/06tl+LEyDFieXVQmbzQlAuZuobmoEKselnQnX6/S\n" +
                    "oq3IjnSY7SoobEOdyPSSXVLBKwab9cCCiep0oIvtW2NMIdMvIikx5QachezNmW65\n" +
                    "ICDNFqY1adjc0fc62OfS6+lUp8miQ65yGQcEiWp229zTLjY8ZAeIeyCkbCf9yNwV\n" +
                    "OmI3+aMIXVnznh2A32n+hROPcVIRDVS5ZYplCBKg+86HZn0t2jVuhY4mNpdJhW1X\n" +
                    "x+e7u/Ekf3FJBoxvefBS7qtdDxGiQywYohztOixeoBlPM53NDA5KEzoBJBN12fZP\n" +
                    "v00FJBYAxG9zBunJo8c3p4WmxOeihxQbU5oiRmZC5nH/G7q5Jb6OAQZaf5z80K95\n" +
                    "Kr/NOLKr9YiSvhuhFzEOMcmH4sEjphhfhyyXHJXBjWiVMRYtCMdRgLOhwH4gOCPA\n" +
                    "bXrG/xngQ4f4kME95v3rdTtlmpzTdrjMY/2DoCHi9+VBlSsQGFn5bqVqtgzJC9Vo\n" +
                    "L1w8Q9Kg8BgTj/25zduX338ovq7WfNzGsdnEvkmKWC3F3xZUcWv0wGrllBZ7l38E\n" +
                    "d1kKV46WsXANimlpiuc9e+Ez5asHNzN6y1eGC5ywbNap3llBX0GZ3kXMSQwb7wlR\n" +
                    "t8h3Ls9yy2SJ10ylKic3b9fJgqsSS+/4jboZ3uRg93ydPPmcDWPhlrpactgg2V4h\n" +
                    "vla1Bz9XmBr8T/Rw1Cr5NwlsyJ8qvXnbsZk842pMaXkSDIEPv2W66mihS+DwamNG\n" +
                    "6eWDoqzTt6ZT9qz9LSV7telt+iPYVRxPPcMtjZ+h9T5J3qvLmEUzNrmMPx139tGh\n" +
                    "SGnMxtlxeEYJcPt7Ec/RMcc9LysdMNUh9dsw1nn6FfSUE+/mcCDcRYycwTecOo1h\n" +
                    "uDsjC7PxuZgcNISFwCcbMXUpTyLsUyPTOlhy++NK++bM3E9s0ALige+d/FTXDWgM\n" +
                    "CZlNucFBOrlx8XwSeBapSc+KXpMkvNWcQge7dMO/XP7jX0u5qZYlbvfDdtKT5BEC\n" +
                    "SCbEeIQgCCLFrfelNEB5TLecd3tpA7ODGizJ8fNEJqxM7tMVjR03h02hXzguicFq\n" +
                    "RLOAV0bkwXy9a3CUCmcekHNc6E5qCjoZj/WzH1ZtXK4rlPtmZ7DwZ9oaWtOOzBRl\n" +
                    "DPhIWFl7VoiT3EHQnj6HyOwleMheXsOO9HgJLgby57IOTHBVniC7SpNIv6qMbo3J\n" +
                    "bnFdvhtS0XrnXAy71FWGTL0uGBwOGejstuBJg2MLoOqS6gZCH6V1M+vfcs06hcF7\n" +
                    "LRJ9DoJ3oyupxLE+67aBouEO8AsPg2JLH9WmzJXKMo8nyuVXzLDeDDiDnRro1sc5\n" +
                    "MdyKkT8KeT6yhu0IHavxd1rqZnXqR0XPo64IOhLLAhOo4y3KnU2mysMk+KItQipp\n" +
                    "quHoxj1wOc025bhl+lYDyHdmuMJvzYhbDDMaNWr/8KUHojalFi4D/+nkAzLgAc8w\n" +
                    "r2sz9+kG4UwTLShuthLY0J8f+cDNGZsz/8OpJcUwDk+lqhBMzqISUYAXuOzXLAad\n" +
                    "3LqFtkyYv9ZPyMwVizZg4bEmRAzC+EPXQFkv7sGOtbj/sfLVHPevuoKQoZbENLCZ\n" +
                    "UqIxLZe/bye3S+q/uZcNZ7U6R9zAew50gg/b/fyjdg9WOPvQpIper/IXEWC3PZus\n" +
                    "NoPBF3LF2UH5hUTSPSCdtByeuwCFkaBDYl9FiEcXLq2126rI55+2+jDAwj89kBoq\n" +
                    "lGq2oZtpKSeR0NQqVnMS1vatiI0GXB7SSFbCtPOSRpiKPvh6bj32sI5Ytx3ZFDvE\n" +
                    "-----END RSA PRIVATE KEY-----\n", sftpPassphrase);
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
            for(File file : files){
                channelSftp.put(new FileInputStream(file.getName()), file.getName(), ChannelSftp.OVERWRITE);
            }
            log.info("File transfered successfully to host.");
        } catch (Exception ex) {
            log.error("Exception found while transfer the response.", ex);
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


    private void getBlobFileAndCreateMetadataFile(ArrayList<File> metadataFiles, BlobStoreFiles.File blobStoreFile) {
        String nameMetadataFile;
        if(blobStoreFile.getFileNameOnly().equals("naq-aggregated-netex.zip")){
            nameMetadataFile = "naq-aggregated-netex-metadonnes.csv";
            metadataFiles.add(createMetadataFile.createMetadataFile(blobStoreFile.getFileNameOnly(), nameMetadataFile, blobStoreFile.getReferential()));
        }
        else if(blobStoreFile.getFileNameOnly().equals("naq-aggregated-gtfs.zip")){
            nameMetadataFile = "naq-aggregated-gtfs-metadonnes.csv";
            metadataFiles.add(createMetadataFile.createMetadataFile(blobStoreFile.getFileNameOnly(), nameMetadataFile, blobStoreFile.getReferential()));
        }
        else{
            nameMetadataFile = blobStoreFile.getReferential() + "-metadonnes.csv";
            metadataFiles.add(createMetadataFile.createMetadataFile(blobStoreFile.getFileNameOnly(), nameMetadataFile, blobStoreFile.getReferential()));
        }
    }
}
