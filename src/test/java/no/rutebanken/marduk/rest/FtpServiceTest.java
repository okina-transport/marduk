package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.services.FtpService;
import org.junit.jupiter.api.*;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

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

import static org.assertj.core.api.Assertions.assertThat;

class FtpServiceTest {

    private static final String FILE_TO_UPLOAD_PATH = "test_file.txt";
    private static final String USER = "bob";
    private static final String PASSWORD = "12345";
    private final FtpService ftpService = new FtpService();
    private Path workingDir;
    private FakeFtpServer fakeFtpServer;
    private int port = -1;

    @BeforeEach
    void init() {
        this.workingDir = Paths.get("src/test/resources");
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);  // use any free port

        FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry("/tmp"));
        fileSystem.add(new FileEntry("/tmp/export_gtfs_371.zip"));
        fakeFtpServer.setFileSystem(fileSystem);

        UserAccount userAccount = new UserAccount(USER, PASSWORD, "/tmp");
        fakeFtpServer.addUserAccount(userAccount);

        fakeFtpServer.start();
        port = fakeFtpServer.getServerControlPort();
    }

    @AfterEach
    void after() {
        fakeFtpServer.stop();
    }


    @Test
    void restExportUpload() throws Exception {
        String ftpUrl = String.format("ftp://%s:%s@localhost:%d/export_gtfs_371.zip", USER, PASSWORD, port);

        URLConnection urlConnection = new URL(ftpUrl).openConnection();
        OutputStream out = urlConnection.getOutputStream();

        File file = this.workingDir.resolve(FILE_TO_UPLOAD_PATH).toFile();
        Files.copy(file.toPath(), out);
        out.close();

        assertThat(file).exists();
    }


    @Test
    void uploadFile() throws Exception {
        File file = this.workingDir.resolve(FILE_TO_UPLOAD_PATH).toFile();
        ftpService.uploadFile(file, "ftp://localhost:" + port, USER, PASSWORD);
    }

    @Test()
    void uploadFileFileNotFound() {
        Assertions.assertThrows(FileNotFoundException.class, () -> {
            File file = this.workingDir.resolve(FILE_TO_UPLOAD_PATH + "_2").toFile();
            ftpService.uploadFile(file, "ftp://localhost:" + port, USER, PASSWORD);
        });
    }

    @Test()
    void uploadFileInvalidFtpUrl() {
        Assertions.assertThrows(InvalidPropertiesFormatException.class, () -> {
            File file =  this.workingDir.resolve(FILE_TO_UPLOAD_PATH).toFile();
            ftpService.uploadFile(file, "http:////localhost", USER, PASSWORD);
        });
    }

    @Test
    void uploadStream() throws Exception {
        FileInputStream fis = new FileInputStream(this.workingDir.resolve(FILE_TO_UPLOAD_PATH).toFile());
        boolean uploaded = ftpService.uploadStream(fis, "ftp://localhost", USER, PASSWORD, port, "", "remotefilenameXX.zip");
        assertThat(uploaded).isTrue();
    }

    @Test
    @Disabled
    void testUploadSftp() throws Exception {
        FileInputStream fis = new FileInputStream(this.workingDir.resolve("NRI 20160219.rar").toFile());
        ftpService.uploadStreamSFTP(fis, "fillme", "fillme", "fillme", 22, "fillme", "NRI 20160219.rar");
    }

}
