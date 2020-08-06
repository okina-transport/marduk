package no.rutebanken.marduk.rest;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;

public class FtpServiceTest {

    private static final String USER = "bob";
    private static final String PASSWORD = "12345";
    @Test
    public void restExportUpload() throws Exception {
        String ftpUrl = String.format("ftp://%s:%s@localhost:%d/export_gtfs_371.zip", USER, PASSWORD, 21);


        URLConnection urlConnection = new URL(ftpUrl).openConnection();
        OutputStream out = urlConnection.getOutputStream();

        File file = new File("/tmp/export_gtfs_371.zip");
        Files.copy(file.toPath(), out);
        out.close();

        Assert.assertTrue(file.exists());
    }
}
