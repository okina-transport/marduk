package no.rutebanken.marduk.services;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import no.rutebanken.marduk.Utils.SendMail;
import no.rutebanken.marduk.Utils.SlackNotification;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import spark.utils.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.InvalidPropertiesFormatException;

@Component
public class FtpService {

    @Autowired
    private SlackNotification slackNotification;

    @Autowired
    private SendMail sendMail;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Uploads a file to a ftp server location
     *
     * @param file
     * @param ftpUrl
     * @param user
     * @param password
     * @throws Exception
     */
    public void uploadFile(File file, String ftpUrl, String user, String password) throws Exception {
        if (file == null || !file.exists()) {
            String msg = file != null ? "File " + file.getName() + "not found for ftp upload " : "No file defined for upload: null file";
            throw new FileNotFoundException(msg);
        }
        String[] tokens = ftpUrl.split("//");
        if (tokens == null || tokens.length != 2 || !tokens[0].equalsIgnoreCase("ftp:")) {
            throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
        }

        String fullUrl = String.format(tokens[0] + "//%s:%s@" + tokens[1] + "/%s", user, password, file.getName());
        URLConnection urlConnection = new URL(fullUrl).openConnection();
        try (OutputStream out = urlConnection.getOutputStream();) {
            Files.copy(file.toPath(), out);
        }
    }


    public boolean uploadStream(InputStream uploadStream, String ftpUrl, String user, String password, Integer port, String destinationPath, String ftpFileName) throws Exception {
        FTPSClient ftpClient = new FTPSClient();
        String ftpHost = parseHostFromFtpUrl(ftpUrl);
        ftpClient.connect(ftpHost, port);
        ftpClient.login(user, password);
        ftpClient.enterLocalPassiveMode();
        ftpClient.execPROT("P");

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        String ftpFilePath = parseFilePathFromFtpUrl(ftpUrl);

        String ftpDestinationPath = getFtpDestinationPath(destinationPath);

        boolean uploaded;
        if (StringUtils.isNotEmpty(destinationPath)) {
            uploaded = ftpClient.storeFile(ftpFilePath + ftpDestinationPath + ftpFileName, uploadStream);
        } else {
            uploaded = ftpClient.storeFile(ftpFilePath + "/" + ftpFileName, uploadStream);
        }

        logger.info(ftpClient.getReplyString());

        return uploaded;
    }

    private String getFtpDestinationPath(String destinationPath) {
        if (StringUtils.isNotEmpty(destinationPath)) {
            if (!destinationPath.startsWith("/")) {
                destinationPath = "/" + destinationPath;
            }
            if (!destinationPath.endsWith("/")) {
                destinationPath = destinationPath + "/";
            }
        }
        return destinationPath;
    }


    private String parseHostFromFtpUrl(String ftpUrl) throws InvalidPropertiesFormatException {
        String[] tokens = ftpUrl.split("//");
        if (tokens == null || tokens.length != 2 || !tokens[0].equalsIgnoreCase("ftp:")) {
            throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
        }
        return tokens[1].split("/")[0];
    }

    private String parseFilePathFromFtpUrl(String ftpUrl) throws InvalidPropertiesFormatException {
        if (ftpUrl.contains("//")) {
            String[] tokens = ftpUrl.split("//");
            if (tokens == null || tokens.length != 2 || !tokens[0].equalsIgnoreCase("ftp:")) {
                throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
            }
            return tokens[1] != null && tokens[1].contains("/") ? tokens[1].split("/")[1] : "";
        } else {
            return ftpUrl;
        }
    }

    public void uploadStreamSFTP(InputStream streamToUpload, String ftpUrl, String login, String password, Integer port, String destinationPath, String sftpFileName) throws Exception {
        String ftpFilePath = parseFilePathFromFtpUrl(ftpUrl);

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
//            jsch.addIdentity(sftpPrivateKey, sftpPassphrase);
            session = jsch.getSession(login, ftpFilePath, port);
            session.setPassword(password);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel("sftp");
            channel.connect();
            logger.info("SFTP channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            if (StringUtils.isNotEmpty(destinationPath)) {
                channelSftp.cd(destinationPath);
            }

            channelSftp.put(streamToUpload, sftpFileName);

            logger.info("File transfered successfully to host.");
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("Exception found while transfer the response.", ex.getMessage());
            slackNotification.sendSlackNotificationTitleAndMessage(SlackNotification.NOTIFICATION_CHANNEL, "Erreur upload du fichier: " + sftpFileName + " sur: " + ftpFilePath, "Le fichier n'a pas pu être exporté.");
            sendMail.sendEmail("Erreur upload du fichier: " + sftpFileName + " sur: " + ftpFilePath, "Le fichier n'a pas pu être exporté.", null);

        } finally {
            channelSftp.exit();
            logger.info("SFTP Channel exited.");
            channel.disconnect();
            logger.info("Channel disconnected.");
            session.disconnect();
            logger.info("Host Session disconnected.");
        }
    }
}
