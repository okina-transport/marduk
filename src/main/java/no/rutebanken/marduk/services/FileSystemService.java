package no.rutebanken.marduk.services;

import no.rutebanken.marduk.IDFMNetexIdentifiants;
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

import javax.inject.Inject;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
        File latestFile = null;
        Date latestDate = null;

        for (File zipFile : zipFiles) {

            String[] splitTimestamp = zipFile.getName().split("_");
            String strTimestamp = splitTimestamp[splitTimestamp.length - 1].replaceAll("[a-zA-Z.]", "");

            try {
                Date currentDate = sdf.parse(strTimestamp);

                if (latestDate == null || currentDate.after(latestDate)) {
                    latestFile = zipFile;
                    latestDate = currentDate;
                }
            } catch (ParseException ignored) {
            }
        }

        if (latestFile != null) {
            exchange.getIn().setHeader(FILE_NAME, latestFile.getName());
        }

        return latestFile;
    }

    public File getOfferFile(Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        String referential = exchange.getIn().getHeader(OKINA_REFERENTIAL, String.class);
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

}
