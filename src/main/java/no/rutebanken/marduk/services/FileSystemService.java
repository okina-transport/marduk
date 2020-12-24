package no.rutebanken.marduk.services;

import no.rutebanken.marduk.IDFMNetexIdentifiants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.routes.blobstore.BlobStoreRoute;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
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

    public List<File> getValidationFiles(Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String jobId = exchange.getIn().getHeader(CHOUETTE_JOB_ID, String.class);
        FileSystemResource fileSystemResource = new FileSystemResource(chouetteStoragePath + "/" + referential + "/data/" + jobId);

        List<File> validationFiles = null;
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            validationFiles = Arrays.stream(files)
                    .filter(file -> !file.getName().toLowerCase().endsWith(".zip") &&
                                    !file.getName().toLowerCase().endsWith(".csv"))
                    .collect(Collectors.toList());
        }

        return validationFiles;
    }

    public File getOfferFileConcerto(Exchange exchange) {
        String jobId = exchange.getIn().getHeader(CHOUETTE_JOB_ID, String.class);
        FileSystemResource fileSystemResource = new FileSystemResource(chouetteStoragePath + "/admin/data/" + jobId);

        File offerFile = null;
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            for (final File file : files) {
                if (file.getName().toLowerCase().endsWith(".csv")) {
                    offerFile = file;
                }
            }
        }

        if (offerFile != null) {
            exchange.getIn().setHeader(FILE_NAME, offerFile.getName());
        }

        return offerFile;
    }

}
