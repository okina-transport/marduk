package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ConfigurationFtp;
import no.rutebanken.marduk.routes.importAutomatics.ImportConfigurationRouteBuilder;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FtpImportDynamicFileIntegrationTest {

    @Test
    void testGetFileFromFTP_WithDynamicRegex_ShouldSelectMostRecent() {
        ConfigurationFtp config = new ConfigurationFtp();
        config.setFilename("^export_.*\\.zip");
        config.setDynamicFilename(true);
        config.setLastTimestamp(LocalDateTime.of(2023, 1, 1, 0, 0));

        // Simulation des fichiers présents sur le FTP
        FTPFile oldFile = createMockFtpFile("export_v1.xml", 2023, 10, 20);
        FTPFile oldFile2 = createMockFtpFile("export_v1.zip", 2023, 10, 21);
        FTPFile oldFile3 = createMockFtpFile("export_v0.zip", 2023, 10, 19);
        FTPFile recentFile = createMockFtpFile("export_v2.xml", 2023, 10, 25);
        FTPFile otherFile = createMockFtpFile("ignore_me.txt", 2023, 10, 26);

        FTPFile[] ftpFiles = new FTPFile[]{oldFile, oldFile2, oldFile3, recentFile, otherFile};

        Optional<FTPFile> selectedFile = ImportConfigurationRouteBuilder.selectMostRecentFile(ftpFiles, config);

        assertThat(selectedFile).isPresent();
        assertThat(selectedFile.get().getName()).isEqualTo("export_v1.zip");

        LocalDateTime selectedTimestamp = LocalDateTime.ofInstant(
                selectedFile.get().getTimestamp().toInstant(),
                selectedFile.get().getTimestamp().getTimeZone().toZoneId()
        );
        assertThat(selectedTimestamp).isAfter(config.getLastTimestamp());
    }

    @Test
    void testGetFileFromFTP_WithStaticName_ShouldMatchExact() {
        ConfigurationFtp config = new ConfigurationFtp();
        config.setFilename("exact_name.xml");
        config.setDynamicFilename(false);

        // Simulation des fichiers présents sur le FTP
        FTPFile oldFile = createMockFtpFile("exact_name.xml", 2023, 10, 10);
        FTPFile recentFile = createMockFtpFile("exact_name.xml", 2023, 10, 20);
        FTPFile otherFile = createMockFtpFile("other_name.xml", 2023, 10, 20);

        FTPFile[] ftpFiles = new FTPFile[]{oldFile, recentFile, otherFile};

        Optional<FTPFile> selectedFile = ImportConfigurationRouteBuilder.selectMostRecentFile(ftpFiles, config);

        assertThat(selectedFile).isPresent();
        assertThat(selectedFile.get().getName()).isEqualTo("exact_name.xml");
    }

    /**
     * Utilitaire pour créer un faux fichier FTP avec un timestamp spécifique
     */
    private FTPFile createMockFtpFile(String name, int year, int month, int day) {
        FTPFile file = new FTPFile();
        file.setName(name);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(year, month - 1, day, 12, 0);
        file.setTimestamp(cal);
        return file;
    }
}
