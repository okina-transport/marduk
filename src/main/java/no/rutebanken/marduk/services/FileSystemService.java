package no.rutebanken.marduk.services;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.OKINA_REFERENTIAL;

@Service
public class FileSystemService {

    @Value("${tiamat.storage.path:/srv/docker-data/data/tiamat}")
    private String tiamatStoragePath;

    @Value("${chouette.storage.path:/srv/docker-data/data/chouette}")
    private String chouetteStoragePath;

    @Autowired
    CacheProviderRepository providerRepository;

    private Logger logger = LoggerFactory.getLogger(this.getClass());


    public File getTiamatFile(String filename) {
        return new File(tiamatStoragePath + "/" + filename);
    }

    public File getLatestStopPlacesFile(Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        FileSystemResource fileSystemResource = new FileSystemResource(tiamatStoragePath);
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class).replace("MOSAIC_", "").replace("mosaic_", "");
        logger.info("------ referential : " + referential);

        Provider provider = providerRepository.getByReferential(referential).orElseThrow(() -> new RuntimeException("Aucun provider correspondant au referential " + referential));;
        logger.info("------ provider: " + provider.name + " with code idfm/filiale => " + ((provider.getChouetteInfo() != null)? provider.getChouetteInfo().getCodeIdfm() : "no code idfm found" ));
        String idSite = provider.getChouetteInfo().getCodeIdfm();

        logger.info("------ idsite : " + idSite);
        logger.info("------ filename a peu presque : ARRET_" + idSite + ".zip");

        List<File> zipFiles = new ArrayList<File>();
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            String filename = null;
            for (final File file : files) {
                filename = file.getName().toLowerCase();
                if (filename.endsWith(".zip") && filename.startsWith("arret_" + idSite.toLowerCase())) {
                    zipFiles.add(file);
                }
            }
        }

        zipFiles.sort(Comparator.comparing(File::getName));
        File latestFile = zipFiles.get(zipFiles.size()-1);
        exchange.getIn().setHeader(FILE_NAME, latestFile.getName());

        return latestFile;
    }

    public File getOfferFile(Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class);
        if (StringUtils.isNotBlank(referential) && !referential.startsWith("mosaic_")) {
            referential = "mosaic_" + referential;
        }
        String jobId = exchange.getIn().getHeader(CHOUETTE_JOB_ID, String.class);
        FileSystemResource fileSystemResource = new FileSystemResource(chouetteStoragePath + "/" + referential + "/data/" + jobId);

        File offerFile = null;
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            for (final File file : files) {
                if (file.getName().toLowerCase().endsWith(".zip") || file.getName().toLowerCase().endsWith(".csv")) {
                    offerFile = file;
                }
            }
        }

        if (offerFile != null) {
            exchange.getIn().setHeader(FILE_NAME, offerFile.getName());
        }

        return offerFile;
    }

    public String getChouetteStoragePath(){
        return chouetteStoragePath;
    }

    public List<Path> getAllFilesFromLocalStorage(String prefix, String fileExtension){
        try {
            return Files.walk(Paths.get(chouetteStoragePath))
                 .filter(p -> p.toString().startsWith(chouetteStoragePath+"/"+prefix) && p.getFileName().toString().endsWith(fileExtension))
                 .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Récupération fichiers localStorage impossible: " + e);
            return new ArrayList();
            }
        }

    public InputStream getFile(String fileName)  {

        try {
            return new FileInputStream(getOrCreateFilePath(fileName).toFile());
        } catch (FileNotFoundException e) {
            logger.error("Récupération fichiers localStorage impossible: " + e);
            throw new IllegalArgumentException("Fichier :" + fileName);
        }

    }

    public Path getOrCreateFilePath(String fileName){
        try {
          List<Path> pathList = Files.walk(Paths.get(chouetteStoragePath))
                .filter(p -> p.toString().equals(chouetteStoragePath+"/"+fileName))
                .collect(Collectors.toList());

            if (pathList.isEmpty()) {
                logger.info ("Création fichier: " + fileName);
                File newFile = new File(chouetteStoragePath+"/"+fileName);
                newFile.getParentFile().mkdirs();
                Path newPath = newFile.toPath();
                Files.createFile(newPath);
                return newPath;
            }
            return pathList.get(0);
        } catch (IOException e) {
            logger.error("Récupération fichiers localStorage impossible: " + e);
            throw new IllegalArgumentException("Fichier non trouvé:" + fileName);
        }
    }



    }


