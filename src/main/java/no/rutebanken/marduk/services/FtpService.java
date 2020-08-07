package no.rutebanken.marduk.services;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.InvalidPropertiesFormatException;

@Component
public class FtpService {

    /**
     * Uploads a file to a ftp server location
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
        try(OutputStream out = urlConnection.getOutputStream();) {
            Files.copy(file.toPath(), out);
        }
    }
}
