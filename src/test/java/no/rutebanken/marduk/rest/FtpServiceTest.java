package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.services.FtpService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.InvalidPropertiesFormatException;

@Ignore
public class FtpServiceTest {

    FtpService ftpService = new FtpService();
    private Path workingDir;


    @Before
    public void init() {
        this.workingDir = Paths.get("src/test/resources");
    }

    private static final String FILE_TO_UPLOAD_PATH = "load fil";
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

    @Test
    public void uploadStream() throws Exception {
        FileInputStream fis = new FileInputStream(new File(FILE_TO_UPLOAD_PATH));
        boolean uploaded = ftpService.uploadStream(fis, "ftp://localhost", USER, PASSWORD, 22, "", "remotefilenameXX.zip");
        Assert.assertTrue(uploaded);
    }

    @Test
    public void testUploadSftp() throws Exception {
        FileInputStream fis = new FileInputStream(this.workingDir.resolve("NRI 20160219.rar").toFile());
        ftpService.uploadStreamSFTP(fis, "fillme", "fillme", "fillme", 22, "fillme", "NRI 20160219.rar");
    }

}
