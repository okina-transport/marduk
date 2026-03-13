package no.rutebanken.marduk.services.processors;

import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static no.rutebanken.marduk.Constants.*;

@Component
public class MergeOfferAndFaresInGTFSProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOfferAndFaresInGTFSProcessor.class);
    public static final String GTFS_V2_DIR = "gtfsv2";

    private final String faresStoragePath;
    private final String chouetteStoragePath;

    public MergeOfferAndFaresInGTFSProcessor(@Value("${fares.storage.path:/srv/docker-data/data/fares}") String faresStoragePath,
                                             @Value("${chouette.storage.path:/srv/docker-data/data/chouette}") String chouetteStoragePath) {
        this.faresStoragePath = faresStoragePath;
        this.chouetteStoragePath = chouetteStoragePath;
    }


    @Override
    public void process(Exchange exchange) throws Exception {
        LOGGER.info("Starting merge of offer file and fares file");
        String faresExportDir = exchange.getIn().getHeader(FARES_DIRECTORY, String.class);
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String chouetteFileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        if (!chouetteFileName.endsWith(".zip")){
            chouetteFileName = chouetteFileName + ".zip";
        }

        String faresFileName = referential.replace("mobiiti_","").toUpperCase() + ".zip";
        String chouetteJobId = exchange.getIn().getHeader(JOB_ID, String.class);

        Path source = Path.of(faresStoragePath, GTFS_V2_DIR, faresExportDir, faresFileName);
        String chouetteJobDirectory = chouetteStoragePath + "/" + referential + "/data/" + chouetteJobId;
        Path destination = Path.of(chouetteJobDirectory + "/" + faresFileName);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        ZipFileUtils.unzipAndDeleteAll(chouetteJobDirectory);
        ZipFileUtils.zipFilesWithExtensionInFolder(chouetteJobDirectory,chouetteJobDirectory + "/" + chouetteFileName,".txt");
        ZipFileUtils.deleteFilesByExtension(chouetteJobDirectory, ".txt");
        LOGGER.info("Merge of offer file and fares file completed successfully");
    }
}
