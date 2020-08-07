package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.services.FtpService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.InvalidPropertiesFormatException;

public class FtpServiceTest {

    FtpService ftpService = new FtpService();

    private static final String FILE_TO_UPLOAD_PATH = "/tmp/export_gtfs_371.zip";
    private static final String USER = "bob";
    private static final String PASSWORD = "12345";
    @Test
    public void restExportUpload() throws Exception {
        String ftpUrl = String.format("ftp://%s:%s@localhost:%d/export_gtfs_371.zip", USER, PASSWORD, 21);

        URLConnection urlConnection = new URL(ftpUrl).openConnection();
        OutputStream out = urlConnection.getOutputStream();

        File file = new File(FILE_TO_UPLOAD_PATH);
        Files.copy(file.toPath(), out);
        out.close();

        Assert.assertTrue(file.exists());
    }


    @Test
    public void uploadFile() throws Exception {
        File file = new File(FILE_TO_UPLOAD_PATH);
        ftpService.uploadFile(file, "ftp://localhost", USER, PASSWORD);
    }

    @Test(expected = FileNotFoundException.class)
    public void uploadFileFileNotFound() throws Exception {
        File file = new File(FILE_TO_UPLOAD_PATH);
        ftpService.uploadFile(file, "ftp://localhost", USER, PASSWORD);
    }

    @Test(expected = InvalidPropertiesFormatException.class)
    public void uploadFileInvalidFtpUrl() throws Exception {
        File file = new File(FILE_TO_UPLOAD_PATH);
        ftpService.uploadFile(file, "http:////localhost", USER, PASSWORD);
    }

}
