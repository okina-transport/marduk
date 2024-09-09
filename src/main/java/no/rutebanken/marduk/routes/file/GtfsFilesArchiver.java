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
     * Read a gtfs, unzip it, and saves trips.txt,stop_times.txt, calendar.txt and calendar_dates into chouetteDir/mobiiti_technique/[trips|stop_times|calendar|calendar_dates]/"organisationName" directory
     * @param gtfsZipFile
     *  the gtfs to read
     * @param organisation
     *  the organisation name
     */
    public void archiveGtfsData(File gtfsZipFile, String organisation)  {

        try{
            InputStream targetStream = new FileInputStream(gtfsZipFile);
            File tmpDir = new File("/tmp", String.valueOf(UUID.randomUUID()));
            tmpDir.mkdirs();

            // unzip GTFS archive in tmpDir
            ZipFileUtils.unzipFile(targetStream,tmpDir.getAbsolutePath());

            // copy trips
            File originalTripsFile = new File(tmpDir, "trips.txt");
            File archivedTripsFile = new File(getDestinationDir(organisation, "trips"), getDestinationFileName("trips_"));
            archivedTripsFile.mkdirs();
            logger.info("Archiving trips.txt from zip : {} to file : {}", gtfsZipFile.getAbsolutePath(), archivedTripsFile.getAbsolutePath());
            Files.copy(originalTripsFile.toPath(), archivedTripsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // copy stop_times
            File originalStopTimesFile = new File(tmpDir, "stop_times.txt");
            File archivedStopTimesFile = new File(getDestinationDir(organisation, "stop_times"), getDestinationFileName("stop_times_"));
            archivedStopTimesFile.mkdirs();
            logger.info("Archiving stop_times.txt from zip : {} to file : {}", gtfsZipFile.getAbsolutePath(), archivedStopTimesFile.getAbsolutePath());
            Files.copy(originalStopTimesFile.toPath(), archivedStopTimesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);


            // copy calendar
            File originalCalendarFile = new File(tmpDir, "calendar.txt");
            if (originalCalendarFile.exists()){
                File archivedCalendarFile = new File(getDestinationDir(organisation, "calendar"), getDestinationFileName("calendar_"));
                archivedCalendarFile.mkdirs();
                logger.info("Archiving calendar.txt from zip : {} to file : {}", gtfsZipFile.getAbsolutePath(), archivedCalendarFile.getAbsolutePath());
                Files.copy(originalCalendarFile.toPath(), archivedCalendarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }


            // copy calendar_dates
            File originalCalendarDatesFile = new File(tmpDir, "calendar_dates.txt");
            if (originalCalendarDatesFile.exists()){
                File archivedCalendarDatesFile = new File(getDestinationDir(organisation, "calendar_dates"), getDestinationFileName("calendar_dates_"));
                archivedCalendarDatesFile.mkdirs();
                logger.info("Archiving calendar_dates.txt from zip : {} to file : {}", gtfsZipFile.getAbsolutePath(), archivedCalendarDatesFile.getAbsolutePath());
                Files.copy(originalCalendarDatesFile.toPath(), archivedCalendarDatesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }


            ZipFileUtils.deleteDirectory(tmpDir);
        }catch (Exception e){
            logger.error("Error while archiving trips.txt and stop_times.txt", e);
        }

    }

    /**
     * Delete the organisation directory (in mobiiti_technique/xxxx) and all its files
     * @param organisationName
     *  the organisation for which the directory must be deleted
     */
    public void cleanOrganisationArchivedFiles(String organisationName, String fileType){
        File organisationDirectory = new File(getDestinationDir(organisationName, fileType));
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
