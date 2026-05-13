package no.rutebanken.marduk.services.processors;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.utils.FileUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

import static no.rutebanken.marduk.Constants.CLEAN_INPUT_NETEX_ZIP;
import static no.rutebanken.marduk.Constants.INPUT_OFFER_ZIP_FILE_PATH;

@Component
public class FileValidationProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileValidationProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        String importType = (String) exchange.getIn().getHeader("importType");
        Path path = FileUtils.createTemporaryFile(exchange);
        Set<String> files = ZipFileUtils.listFilesInZip(path.toFile());
        boolean invalidZipInput = isZipContainingInvalidFiles(files, importType);
        if (invalidZipInput) {
            exchange.getIn().setHeader(CLEAN_INPUT_NETEX_ZIP, Boolean.TRUE);
        }
        exchange.getIn().setHeader(INPUT_OFFER_ZIP_FILE_PATH, path);
    }

    private boolean isZipContainingInvalidFiles(Set<String> files, String importType ) {
        boolean isInvalid = false;
        if (Strings.CI.equals("gtfs",importType)) {
            for (String file : files) {
                if (!file.endsWith(".txt") && !file.equalsIgnoreCase("locations.geojson")) {
                    String errorMsg = "GTFS file containing non-txt file:" + file;
                    LOGGER.error(errorMsg);
                    throw new MardukException(errorMsg);
                }
            }
        } else {
            for (String file : files) {
                if (!file.endsWith(".xml")) {
                    String errorMsg = "Zip containing non-xml file:" + file;
                    LOGGER.error(errorMsg);
                    isInvalid = true;
                }
            }
        }
        return isInvalid;
    }

}
