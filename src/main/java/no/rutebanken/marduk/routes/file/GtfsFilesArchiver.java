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
     * Read a gtfs, unzip it, and saves stop_times.txt into chouetteDir/mobiiti_technique/stop_times/"organisationName" directory
     * @param inputFile
     *  the gtfs to read
     * @param organisation
     *  the organisation name
     */
    public void archiveStopTimes(File inputFile, String organisation)  {

        try{
            InputStream targetStream = new FileInputStream(inputFile);
            String tmpDir = UUID.randomUUID().toString();
            File newDir = new File("/tmp", tmpDir);
            newDir.mkdirs();

            logger.info("Archiving stop_times.txt from zip : " + inputFile.getAbsolutePath());
            ZipFileUtils.unzipFile(targetStream,newDir.getAbsolutePath());
            File originalFile = new File(newDir, "stop_times.txt");
            File archivedFile = new File(getDestinationDir(organisation, "stop_times"), getDestinationFileName("stop_times_"));
            archivedFile.mkdirs();
            logger.info("to file : " + archivedFile.getAbsolutePath());
            Files.copy(originalFile.toPath(), archivedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ZipFileUtils.deleteDirectory(newDir);

        }catch (Exception e){
            logger.error("Error while archiving stop times", e);
        }

    }

    /**
     * Read a gtfs, unzip it, and saves stop_times.txt into chouetteDir/mobiiti_technique/stop_times/"organisationName" directory
     * @param inputFile
     *  the gtfs to read
     * @param organisation
     *  the organisation name
     */
    public void archiveTrips(File inputFile, String organisation)  {

        try{
            InputStream targetStream = new FileInputStream(inputFile);
            String tmpDir = UUID.randomUUID().toString();
            File newDir = new File("/tmp", tmpDir);
            newDir.mkdirs();

            logger.info("Archiving trips.txt from zip : " + inputFile.getAbsolutePath());
            ZipFileUtils.unzipFile(targetStream,newDir.getAbsolutePath());
            File originalFile = new File(newDir, "trips.txt");
            File archivedFile = new File(getDestinationDir(organisation, "trips"), getDestinationFileName("trips_"));
            archivedFile.mkdirs();
            logger.info("to file : " + archivedFile.getAbsolutePath());
            Files.copy(originalFile.toPath(), archivedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ZipFileUtils.deleteDirectory(newDir);

        }catch (Exception e){
            logger.error("Error while archiving trips", e);
        }

    }

    /**
     * Delete the organisation directoty (in mobiiti_technique/stop_times) and all its files
     * @param organisationName
     *  the organisation for which the directory must be deleted
     */
    public void cleanOrganisationStopTimes(String organisationName){
        File organisationDirectory = new File( chouetteStoragePath + "/mobiiti_technique/stop_times/", organisationName.toUpperCase());
        ZipFileUtils.deleteDirectory(organisationDirectory);
    }

    /**
     * Delete the organisation directoty (in mobiiti_technique/trips) and all its files
     * @param organisationName
     *  the organisation for which the directory must be deleted
     */
    public void cleanOrganisationTrips(String organisationName){
        File organisationDirectory = new File( chouetteStoragePath + "/mobiiti_technique/trips/", organisationName.toUpperCase());
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
