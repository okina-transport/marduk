package no.rutebanken.marduk.services;

import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

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

    public File getLatestStopPlacesFile(Exchange exchange) throws ParseException {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        FileSystemResource fileSystemResource = new FileSystemResource(tiamatStoragePath);

        List<File> zipFiles = new ArrayList<File>();
        File[] files = fileSystemResource.getFile().listFiles();

        if (files != null) {
            for (final File file : files) {
                if (file.getName().toLowerCase().endsWith(".zip")) {
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

            Date currentDate = sdf.parse(strTimestamp);

            if (latestDate == null || currentDate.after(latestDate)) {
                latestFile = zipFile;
                latestDate = currentDate;
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
