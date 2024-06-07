package no.rutebanken.marduk.routes.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class GtfsFilesArchiver {

    private static Logger logger = LoggerFactory.getLogger(GtfsFilesArchiver.class);

    @Value("${chouette.storage.path:/srv/docker-data/data/chouette}")
    private String chouetteStoragePath;


    /**
     * Read a gtfs, unzip it, and saves trips.txt and stop_times.txt into chouetteDir/mobiiti_technique/[trips|stop_times]/"organisationName" directory
     * @param gtfsZipFile
     *  the gtfs to read
     * @param organisation
     *  the organisation name
     */
    public void archiveTripsAndStopTimes(File gtfsZipFile, String organisation)  {

        try{
            InputStream targetStream = new FileInputStream(gtfsZipFile);
            File tmpDir = new File("/tmp", String.valueOf(UUID.randomUUID()));
            tmpDir.mkdirs();

            // unzip GTFS archive in tmpDir
            ZipFileUtils.unzipFile(targetStream,tmpDir.getAbsolutePath());

            // copy trips
            File originalTripsFile = new File(tmpDir, "trips.txt");
            File archivedTripsFile = new File(getDestinationDir(organisation, "stop_times"), getDestinationFileName("stop_times_"));
            archivedTripsFile.mkdirs();
            logger.info("Archiving trips.txt from zip : {} to file : {}", gtfsZipFile.getAbsolutePath(), archivedTripsFile.getAbsolutePath());
            Files.copy(originalTripsFile.toPath(), archivedTripsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // copy stop_times
            File originalStopTimesFile = new File(tmpDir, "stop_times.txt");
            File archivedStopTimesFile = new File(getDestinationDir(organisation, "stop_times"), getDestinationFileName("stop_times_"));
            archivedStopTimesFile.mkdirs();
            logger.info("Archiving stop_times.txt from zip : {} to file : {}", gtfsZipFile.getAbsolutePath(), archivedStopTimesFile.getAbsolutePath());
            Files.copy(originalStopTimesFile.toPath(), archivedStopTimesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            ZipFileUtils.deleteDirectory(tmpDir);
        }catch (Exception e){
            logger.error("Error while archiving trips.txt and stop_times.txt", e);
        }

    }

    /**
     * Delete the organisation directory (in mobiiti_technique/stop_times) and all its files
     * @param organisationName
     *  the organisation for which the directory must be deleted
     */
    public void cleanOrganisationStopTimes(String organisationName){
        File organisationDirectory = new File(getDestinationDir(organisationName, "stop_times"));
        ZipFileUtils.deleteDirectory(organisationDirectory);
    }

    /**
     * Delete the organisation directory (in mobiiti_technique/trips) and all its files
     * @param organisationName
     *  the organisation for which the directory must be deleted
     */
    public void cleanOrganisationTrips(String organisationName){
        File organisationDirectory = new File(getDestinationDir(organisationName, "trips"));
        ZipFileUtils.deleteDirectory(organisationDirectory);
    }

    /**
     * Get the destination directory to store stop_times.txt files
     * @param organisation
     *  the organisation name
     * @return
     *  the directory in which the file will be stored
     */
    private String getDestinationDir(String organisation, String fileType){
        return chouetteStoragePath + "/mobiiti_technique/" + fileType + "/" + organisation.toUpperCase();
    }

    /**
     * Get the name of the file that will be saved with pattern : stop_times_"organisationName"_hhMMddhhmmss.txt
     * @return
     *  the file names
     */
    private String getDestinationFileName(String prefix){
        LocalDateTime currDate = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddhhmmss");
        String dateStr = currDate.format(formatter);
        return prefix + dateStr + ".txt";
    }
}
