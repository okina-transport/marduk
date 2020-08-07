package no.rutebanken.marduk.services;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.stereotype.Component;

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


    public boolean uploadStream(InputStream uploadStream, String ftpUrl, String user, String password, String ftpFileName) throws Exception {
        FTPClient ftpClient = new FTPClient();
        String ftpHost = parseHostFromFtpUrl(ftpUrl);
        ftpClient.connect(ftpHost, 21);
        ftpClient.login(user, password);
        ftpClient.enterLocalPassiveMode();

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        String ftpFilePath = parseFilePathFromFtpUrl(ftpUrl);
        boolean uploaded = ftpClient.storeFile(ftpFilePath + "/" + ftpFileName, uploadStream);
        return uploaded;
    }


    private String parseHostFromFtpUrl(String ftpUrl) throws InvalidPropertiesFormatException {
        String[] tokens = ftpUrl.split("//");
        if (tokens == null || tokens.length != 2 || !tokens[0].equalsIgnoreCase("ftp:")) {
            throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
        }
        return tokens[1].split("/")[0];
    }

    private String parseFilePathFromFtpUrl(String ftpUrl) throws InvalidPropertiesFormatException {
        String[] tokens = ftpUrl.split("//");
        if (tokens == null || tokens.length != 2 || !tokens[0].equalsIgnoreCase("ftp:")) {
            throw new InvalidPropertiesFormatException("Invalid ftp url " + ftpUrl + " for file upload");
        }
        return tokens[1] != null && tokens[1].contains("/") ? tokens[1].split("/")[1] : "";
    }
}
