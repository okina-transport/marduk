package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.services.FtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.InvalidPropertiesFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void uploadStream() throws Exception {
        FileInputStream fis = new FileInputStream(this.workingDir.resolve(FILE_TO_UPLOAD_PATH).toFile());
        boolean uploaded = ftpService.uploadStream(fis, "ftp://localhost", USER, PASSWORD, port, "", "remotefilenameXX.zip");
        assertThat(uploaded).isTrue();
    }

    @Test
    void parseHostFromSftpUrl() throws Exception {
        String host = ftpService.parseHostFromFtpUrl("sftp://www.google.fr");
        assertThat(host).isEqualTo("www.google.fr");
    }

    @Test
    void parseHostFromFtpUrl() throws Exception {
        String host = ftpService.parseHostFromFtpUrl("ftp://user:pass@ftp.example.com:21/some/path/file.zip");
        assertThat(host).isEqualTo("ftp.example.com");
    }

    @Test
    void parseHostFromFtpUrlWithoutCredentialsOrPort() throws Exception {
        String host = ftpService.parseHostFromFtpUrl("ftp://localhost/file.zip");
        assertThat(host).isEqualTo("localhost");
    }

    @Test
    void parseHostFromInvalidFtpUrlThrows() {
        assertThatThrownBy(() -> ftpService.parseHostFromFtpUrl("ftp://exa mple.com/file.zip"))
                .isInstanceOf(InvalidPropertiesFormatException.class);
    }

    @Test
    void parseFilePathFromFtpUrl() throws Exception {
        String path = ftpService.parseFilePathFromFtpUrl("ftp://user:pass@ftp.example.com:21/some/path/file.zip");
        assertThat(path).isEqualTo("/some/path/file.zip");
    }

    @Test
    void parseFilePathFromFtpUrlWithoutPath() throws Exception {
        String path = ftpService.parseFilePathFromFtpUrl("ftp://localhost");
        assertThat(path).isEmpty();
    }

    @Test
    void parseFilePathFromInvalidFtpUrlThrows() {
        assertThatThrownBy(() -> ftpService.parseFilePathFromFtpUrl("ftp://exa mple.com/file.zip"))
                .isInstanceOf(InvalidPropertiesFormatException.class);
    }

    @Test
    @Disabled
    void testUploadSftp() throws Exception {
        FileInputStream fis = new FileInputStream(this.workingDir.resolve("NRI 20160219.rar").toFile());
        ftpService.uploadStreamSFTP(fis, "fillme", "fillme", "fillme", 22, "fillme", "NRI 20160219.rar");
    }

}
