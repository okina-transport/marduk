package no.rutebanken.marduk.services;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import no.rutebanken.marduk.utils.SendMail;
import no.rutebanken.marduk.utils.SlackNotification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.util.InvalidPropertiesFormatException;

@Component
public class FtpService {

    @Autowired
    private SlackNotification slackNotification;

    @Autowired
    private SendMail sendMail;

    private static final Logger logger = LoggerFactory.getLogger(FtpService.class);

    public boolean uploadStream(InputStream uploadStream, String ftpUrl, String user, String password, Integer port, String destinationPath, String ftpFileName) throws Exception {
        if (!ftpUrl.startsWith("ftp://")) {
            ftpUrl = "ftp://" + ftpUrl;
        }
        FTPClient ftpClient = new FTPClient();
        String ftpHost = parseHostFromFtpUrl(ftpUrl);
        ftpClient.connect(ftpHost, port);
        ftpClient.login(user, password);
        ftpClient.enterLocalPassiveMode();

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        String ftpFilePath = parseFilePathFromFtpUrl(ftpUrl);

        String ftpDestinationPath = getFtpDestinationPath(destinationPath);

        boolean uploaded;
        if (StringUtils.isNotEmpty(destinationPath)) {
            uploaded = ftpClient.storeFile(ftpFilePath + ftpDestinationPath + ftpFileName, uploadStream);
        } else {
            uploaded = ftpClient.storeFile(ftpFilePath + "/" + ftpFileName, uploadStream);
        }
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


    public String parseHostFromFtpUrl(String ftpUrl) throws InvalidPropertiesFormatException {
        try {
            return URI.create(ftpUrl).getHost();
        } catch (Exception e) {
            logger.error("Error while parsing ftp url", e);
            throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
        }
    }

    public String parseFilePathFromFtpUrl(String ftpUrl) throws InvalidPropertiesFormatException {
        try {
            return URI.create(ftpUrl).getPath();
        } catch (Exception e) {
            logger.error("Error while parsing ftp url", e);
            throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
        }
    }

    public void uploadStreamSFTP(InputStream streamToUpload, String ftpUrl, String login, String password, Integer port, String destinationPath, String sftpFileName) throws Exception {
        if (!ftpUrl.startsWith("sftp://")) {
            ftpUrl = "sftp://" + ftpUrl;
        }
        String ftpFilePath = parseFilePathFromFtpUrl(ftpUrl);
        String ftpHost = parseHostFromFtpUrl(ftpUrl);

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(login, ftpHost, port);
            session.setPassword(password);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel("sftp");
            channel.connect();
            logger.info("SFTP channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            logger.info("SFTP current working directory : {}", channelSftp.getHome());
            if (StringUtils.isNotEmpty(ftpFilePath)) {
                logger.info("Run 'cd {}'", ftpFilePath);
                channelSftp.cd(ftpFilePath);
            }
            if (StringUtils.isNotEmpty(destinationPath)) {
                logger.info("Run 'cd {}'", destinationPath);
                channelSftp.cd(destinationPath);
            }

            channelSftp.put(streamToUpload, sftpFileName);

            logger.info("File transfered successfully to host.");
        } catch (Exception ex) {
            logger.error("Exception found while transfering the response : {}", ex.getMessage());
            slackNotification.sendSlackNotificationTitleAndMessage(SlackNotification.NOTIFICATION_CHANNEL, "Erreur upload du fichier: " + sftpFileName + " sur: " + ftpFilePath, "Le fichier n'a pas pu être exporté.");
            sendMail.sendEmail("Erreur upload du fichier: " + sftpFileName + " sur: " + ftpFilePath, null, "Le fichier n'a pas pu être exporté.", null);

        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
                logger.info("SFTP Channel exited.");
                channel.disconnect();
                logger.info("Channel disconnected.");
                session.disconnect();
                logger.info("Host Session disconnected.");
            }

        }
    }
}
