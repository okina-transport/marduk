package no.rutebanken.marduk.services;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.Job;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

import static no.rutebanken.marduk.Constants.*;

@Service
public class FileSystemService {

    @Value("${tiamat.storage.path:/srv/docker-data/data/tiamat}")
    private String tiamatStoragePath;

    @Value("${chouette.storage.path:/srv/docker-data/data/chouette}")
    private String chouetteStoragePath;

    @Autowired
    CacheProviderRepository providerRepository;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${superspace.name}")
    private String superspaceName;

    @Value("${simulation.name}")
    private String simulationName;


    public File getTiamatFile(Exchange e) {
        String filename = null;
        if (e.getIn().getBody() != null && e.getIn().getBody() instanceof Job){
            Job job = e.getIn().getBody(Job.class);
            filename = tiamatStoragePath + "/" + job.getSubFolder() + "/" + job.getFileName();
        }else{
            filename = tiamatStoragePath + "/" +  e.getIn().getHeader(SUB_FOLDER) + "/" + e.getIn().getHeader(FILE_NAME);
        }

        File file = new File(filename);
        e.getIn().setHeader("fileName", file.getName());
        e.getIn().setHeader(EXPORT_FILE_NAME, file.getName());
        return file;
    }



    public File getLatestStopPlacesFile(Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class).replace(superspaceName.toUpperCase() + "_", "").replace(superspaceName.toLowerCase() + "_", "");
        logger.info("------ referential : " + referential);

        Provider provider = providerRepository.getByReferential(referential).orElseThrow(() -> new RuntimeException("Aucun provider correspondant au referential " + referential));
        logger.info("------ provider: " + provider.name + " with code idfm/filiale => " + ((provider.getChouetteInfo() != null) ? provider.getChouetteInfo().getCodeIdfm() : "no code idfm found"));
        String idSite = provider.getChouetteInfo().getCodeIdfm();

        logger.info("------ idsite : " + idSite);
        logger.info("------ filename a peu presque : ARRET_" + idSite + ".zip");

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
        ExchangeUtils.addHeadersAndAttachments(exchange);
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class);
        if (StringUtils.isNotBlank(referential) && !referential.startsWith(superspaceName + "_") && !referential.startsWith(simulationName + "_")) {
            referential = superspaceName + "_" + referential;
        }

        String jobId = exchange.getIn().getHeader(JOB_ID, String.class);

        logger.info("Get zip and csv files from path : " + chouetteStoragePath + "/" + referential + "/data/" + jobId);
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
        try {
            return Files.walk(Paths.get(chouetteStoragePath + "/" + prefix))
                    .filter(p -> p.toString().startsWith(chouetteStoragePath + "/" + prefix) && p.getFileName().toString().endsWith(fileExtension))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Récupération fichiers localStorage impossible: " + e);
            return new ArrayList<>();
        }
    }

    public InputStream getFile(String fileName) {
        try {
            return new FileInputStream(getOrCreateFilePath(fileName).toFile());
        } catch (FileNotFoundException e) {
            logger.error("Récupération fichiers localStorage impossible: " + e);
            throw new IllegalArgumentException("Fichier :" + fileName);
        }
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

    public Path getOrCreateFilePath(String fileName) {
        try {
            String startingDirectory = getStartingDirectory(fileName);
            File startDir = new File(startingDirectory);

            if(!startDir.exists()){
                Files.createDirectories(startDir.toPath());
            }

            List<Path> pathList = Files.walk(startDir.toPath())
                    .filter(p -> p.toString().equals(chouetteStoragePath + "/" + fileName))
                    .collect(Collectors.toList());

            if (pathList.isEmpty()) {
                File newFile = new File(chouetteStoragePath + "/" + fileName);
                Path newPath = newFile.toPath();
                Files.write(newPath, fileName.getBytes());
                return newPath;
            }
            return pathList.get(0);
        } catch (IOException e) {
            logger.error("Récupération/Création fichier localStorage: " + e);
            throw new IllegalArgumentException("Nom du fichier:" + fileName);
        }
    }

    public boolean deleteDirectoryFromStorage(String directory) {
        try {
            File startDir = new File(chouetteStoragePath + "/" + directory);
            Files.walk(startDir.toPath())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            logger.error("Erreur suppression répertoire: " + directory + e);
        }
        return true;
    }

    public boolean isExists(String fileName) {
        File f = new File(chouetteStoragePath + "/" + fileName);
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
    public FileItem unzipNetexZip(FileItem netexZipFile) throws ZipException, IOException{
        // works only for Netex POI, parking and stop places where there is a single XML file
        File unzipTmpDir = new File("/tmp", String.valueOf(UUID.randomUUID()));
        try (InputStream zipFileStream = netexZipFile.getInputStream()) {
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
            FileItemFactory fac = new DiskFileItemFactory();
            String xmlFilename = unzippedTmpFiles[0].getName();
            FileItem unzippedXml = fac.createItem("file", "text/xml", false, xmlFilename);
            Streams.copy(new FileInputStream(unzippedTmpFiles[0]), unzippedXml.getOutputStream(), true);
            return unzippedXml;
        } finally {
            FileUtils.deleteQuietly(unzipTmpDir);
        }
    }

}


