package no.rutebanken.marduk.services;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.Job;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import static no.rutebanken.marduk.Constants.*;

@Service
public class FileSystemService {

    @Value("${tiamat.storage.path:/srv/docker-data/data/tiamat}")
    private String tiamatStoragePath;

    @Value("${chouette.storage.path:/srv/docker-data/data/chouette}")
    private String chouetteStoragePath;

    @Value("${fares.storage.path:/srv/docker-data/data/fares}")
    private String faresStoragePath;

    @Value("${uttu.storage.path:/srv/docker-data/data/uttu}")
    private Path uttuStoragePath;

    @Autowired
    CacheProviderRepository providerRepository;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${superspace.name}")
    private String superspaceName;

    @Value("${simulation.name}")
    private String simulationName;


    public File getTiamatFile(Exchange e) {
        String filename = null;
        if (e.getIn().getBody() instanceof Job){
            Job job = e.getIn().getBody(Job.class);
            filename = tiamatStoragePath + File.separator + job.getSubFolder() + File.separator + job.getFileName();
        }else if(e.getIn().getHeader(ORIGINAL_JOB) != null){
            Job job = e.getIn().getHeader(ORIGINAL_JOB, Job.class);
            filename = tiamatStoragePath + File.separator + job.getSubFolder() + File.separator + job.getFileName();
        }else if (  e.getIn().getHeader(SUB_FOLDER) != null){
            filename = tiamatStoragePath + File.separator+  e.getIn().getHeader(SUB_FOLDER) + File.separator + e.getIn().getHeader(FILE_NAME);
        }else{
            filename = tiamatStoragePath + "/technique/" + e.getIn().getHeader(FILE_NAME);
        }

        File file = new File(filename);
        e.getIn().setHeader("fileName", file.getName());
        e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
        return file;
    }

    public File getNetexFaresFile(Exchange e) {
        String filename = e.getIn().getHeader(FARES_EXPORT_FILENAME, String.class);
        File file = Path.of(faresStoragePath, "globalDirectory", filename).toFile();
        e.getIn().setHeader("fileName", file.getName());
        e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
        return file;
    }

    public File getLatestStopPlacesFile(Exchange exchange) {
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class).replace(superspaceName.toUpperCase() + "_", "").replace(superspaceName.toLowerCase() + "_", "");
        logger.info("------ referential : {}", referential);

        Provider provider = providerRepository.getByReferential(referential).orElseThrow(() -> new RuntimeException("Aucun provider correspondant au referential " + referential));
        logger.info("------ provider: " + provider.name + " with code idfm/filiale => " + ((provider.getChouetteInfo() != null) ? provider.getChouetteInfo().getCodeIdfm() : "no code idfm found"));
        String idSite = provider.getChouetteInfo().getCodeIdfm();

        logger.info("------ idsite : {}", idSite);
        logger.info("------ filename a peu presque : ARRET_{}.zip", idSite);

        FileSystemResource fileSystemResource = new FileSystemResource(tiamatStoragePath + "/" + provider.name);

        List<File> zipFiles = new ArrayList<>();
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            String filename;
            for (final File file : files) {
                filename = file.getName().toLowerCase();
                if (filename.endsWith(".zip") && filename.startsWith("arret_" + idSite.toLowerCase())) {
                    zipFiles.add(file);
                }
            }
        }

        zipFiles.sort(Comparator.comparing(File::getName));
        File latestFile = zipFiles.get(zipFiles.size() - 1);
        exchange.getIn().setHeader(FILE_NAME, latestFile.getName());

        return latestFile;
    }

    public File getOfferFile(Exchange exchange) {
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class);
        if (StringUtils.isNotBlank(referential) && !referential.startsWith(superspaceName + "_") && !referential.startsWith(simulationName + "_")) {
            referential = superspaceName + "_" + referential;
        }

        String jobId = exchange.getIn().getHeader(JOB_ID, String.class);

        logger.info("Get zip and csv files from path : {}/{}/data/{}", chouetteStoragePath, referential, jobId);
        FileSystemResource fileSystemResource = new FileSystemResource(chouetteStoragePath + "/" + referential + "/data/" + jobId);

        File offerFile = null;
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            for (final File file : files) {
                if (
                        (file.getName().toLowerCase().endsWith(".zip") || file.getName().toLowerCase().endsWith(".csv"))
                            && !file.getName().toLowerCase().endsWith("_orig.zip")
                ) {
                    offerFile = file;
                }
            }
        }

        if (offerFile != null) {
            exchange.getIn().setHeader(FILE_NAME, offerFile.getName());
            exchange.getIn().setHeader(EXPORT_FILE_NAME, offerFile.getName());
            exchange.getIn().setHeader(FILE_HANDLE, offerFile.getAbsolutePath());
        }

        return offerFile;
    }

    public File getImportZipFileByReferentialAndJobId(String referential, String jobId) {
        logger.info("Recovering import zip file for referential: {} and jobId: {}", referential, jobId);
        String importFolder = chouetteStoragePath + "/" + referential + "/data/" + jobId;
        FileSystemResource fileSystemResource = new FileSystemResource(importFolder);

        File[] files = fileSystemResource.getFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));

        if (files == null || files.length == 0) {
            logger.error("Chouette import zip file not found (referential: {}, jobId: {}, importFolder : '{}'", referential, jobId, importFolder);
            return null;
        }

        return files[0];
    }

    public List<Path> getAllFilesFromLocalStorage(String prefix, String fileExtension) {
        try (Stream<Path> walk = Files.walk(Paths.get(chouetteStoragePath + "/" + prefix))) {
            return walk
                    .filter(p -> p.toString().startsWith(chouetteStoragePath + "/" + prefix) && p.getFileName().toString().endsWith(fileExtension))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Récupération fichiers localStorage impossible: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public InputStream getFile(String fileName) {
        try {
            return new FileInputStream(getOrCreateFilePath(fileName).toFile());
        } catch (FileNotFoundException e) {
            logger.error("Récupération fichiers localStorage impossible: {}", e.getMessage());
            throw new IllegalArgumentException("Fichier :" + fileName);
        }
    }


    public static Iterable<CSVRecord> getRecords(ByteArrayOutputStream baos) throws IOException {

        InputStream is1 = new ByteArrayInputStream(baos.toByteArray());
        InputStream is2 = new ByteArrayInputStream(baos.toByteArray());
        String result = IOUtils.toString(is1, StandardCharsets.UTF_8);

        String delimiter = guessDelimiter(result);

        Reader reader = new InputStreamReader(is2);

        return CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(false)
                .setDelimiter(delimiter)
                .build()
                .parse(reader);
    }

    public static String guessDelimiter(String fileContent) {

        String[] lines = fileContent.split("\n");
        String firstLine = lines[0];
        long nbOfSemiColon = firstLine.chars()
                .filter(ch -> ch == ';')
                .count();

        long nbOfComma = firstLine.chars()
                .filter(ch -> ch == ',')
                .count();

        return nbOfSemiColon > nbOfComma ? ";" : ",";


    }

    /**
     * Converts an absolute path to a relative path, starting from Storage Path     *
     *
     * @param absolutePath
     * @return A relative Path
     */
    public String convertToRelativePath(String absolutePath) {
        if (!absolutePath.contains(chouetteStoragePath)) {
            //absolutePath does not seem to be a path from storage Path
            return absolutePath;
        }
        String relativePath = absolutePath.replace(chouetteStoragePath, "");
        if (relativePath.startsWith("/")) {
            return relativePath.substring(1);
        }
        return relativePath;

    }


    /**
     * Returns the directory from which the search should be started.
     * e.g : if the filename is : "71/exports/myFile.zip", the starting rep should be "storagePath/71/exports"
     *
     * @param filename
     * @return the directory from which the search must be started
     */
    private String getStartingDirectory(String filename) {
        // No slash in the filename. Seach must be started from root storage path
        if (!filename.contains("/"))
            return chouetteStoragePath;

        return chouetteStoragePath + "/" + filename.substring(0, filename.lastIndexOf("/"));

    }

    public List<Path> getMardukDirectories() throws IOException {
        Path basePath = Paths.get(chouetteStoragePath);


        try (var stream = Files.list(basePath)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("\\d+"))
                    .collect(Collectors.toList());
        }
    }

    public void cleanUpMardukDirectory(Path organisationPath, int nbOdDays) throws IOException {

        if (!Files.isDirectory(organisationPath)) {
            throw new IllegalArgumentException("Invalid path.");
        }

        Instant thresholdDate = Instant.now().minus(nbOdDays, ChronoUnit.DAYS);

        try (Stream<Path> stream = Files.walk(organisationPath)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".zip"))
                    .forEach(path -> {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                            Instant fileTime = attrs.lastModifiedTime().toInstant();

                            if (fileTime.isBefore(thresholdDate)) {
                                Files.delete(path);
                                logger.info("Deleted : {}", path);
                            }
                        } catch (IOException e) {
                            logger.error("Error while deleting: {} -> {}", path, e.getMessage());
                        }
                    });
        }
    }


    public Path getOrCreateFilePath(String fileName) {
        try {
            String startingDirectory = getStartingDirectory(fileName);
            File startDir = new File(startingDirectory);

            if(!startDir.exists()){
                Files.createDirectories(startDir.toPath());
            }

            try (Stream<Path> walk = Files.walk(startDir.toPath())) {
                List<Path> pathList = walk
                        .filter(p -> p.toString().equals(chouetteStoragePath + File.separator + fileName))
                        .collect(Collectors.toList());

                if (pathList.isEmpty()) {
                    File newFile = new File(chouetteStoragePath + File.separator + fileName);
                    Path newPath = newFile.toPath();
                    Files.write(newPath, fileName.getBytes());
                    return newPath;
                }
                return pathList.getFirst();
            }
        } catch (IOException e) {
            logger.error("Récupération/Création fichier localStorage: {}", e.getMessage());
            throw new IllegalArgumentException("Nom du fichier:" + fileName);
        }
    }

    public boolean deleteDirectoryFromStorage(String directory) {
        File startDir = new File(chouetteStoragePath + File.separator + directory);
        try (Stream<Path> walk = Files.walk(startDir.toPath())) {
            walk.map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            logger.error("Erreur suppression répertoire {} : {}", directory, e.getMessage());
        }
        return true;
    }

    public boolean isExists(String fileName) {
        File f = new File(chouetteStoragePath + File.separator + fileName);
        return f.exists() && !f.isDirectory();
    }

    /**
     * Unzip a NeTEx POI, parking or stop place ZIP containing a single XML file
     * and return this XML file
     *
     * @param netexZipFile NeTEx ZIP to unzip
     * @return unzipped XML file
     * @throws ZipException if ZIP contains 0 or strictly more than one file or
     *         ZIP contains no XML file
     * @throws IOException if unzipping fail
     */
    public Path unzipNetexZip(Path netexZipFile) throws IOException{
        // works only for Netex POI, parking and stop places where there is a single XML file
        File unzipTmpDir = new File("/tmp", String.valueOf(UUID.randomUUID()));
        try (InputStream zipFileStream = Files.newInputStream(netexZipFile)) {
            if (!unzipTmpDir.mkdirs()) {
                throw new IOException("Error creating directory: " + unzipTmpDir);
            }
            ZipFileUtils.unzipFile(zipFileStream, unzipTmpDir.getAbsolutePath());
            File[] unzippedTmpFiles = unzipTmpDir.listFiles();
            if (unzippedTmpFiles == null || unzippedTmpFiles.length != 1) {
                throw new ZipException(String.format("NeTEx ZIP contains %d file(s) but should contain exactly one", unzippedTmpFiles.length));
            }
            if (!unzippedTmpFiles[0].getName().endsWith(".xml")) {
                throw new ZipException("NeTEx ZIP contains no XML file but should contain exactly one");
            }
            Path unzippedXml = Path.of(unzippedTmpFiles[0].getName());
            Files.copy(unzippedTmpFiles[0].toPath(), unzippedXml, StandardCopyOption.REPLACE_EXISTING);
            return unzippedXml;
        } finally {
            FileUtils.deleteQuietly(unzipTmpDir);
        }
    }

    public void copyGtfsFlexZipForUttu(File gtfsZip) throws IOException {
        if (!uttuStoragePath.toFile().exists()) {
            Files.createDirectories(uttuStoragePath);
        }
        Path destFile = uttuStoragePath.resolve(gtfsZip.getName());
        Files.copy(gtfsZip.toPath(), destFile);
    }

}


