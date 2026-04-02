package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.utils.FileUtils;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CLEAN_INPUT_NETEX_ZIP;
import static no.rutebanken.marduk.Constants.INPUT_OFFER_ZIP_FILE_PATH;

public class FileInformations {

    private static final Logger LOG = LoggerFactory.getLogger(FileInformations.class);

    private FileInformations() {
        throw new IllegalStateException("Utility class");
    }

    public static void getObjectUpload(Exchange e) {
        Path inputFilePath = e.getIn().getHeader(INPUT_OFFER_ZIP_FILE_PATH, Path.class);
        if (inputFilePath == null) {
            inputFilePath = FileUtils.createTemporaryFile(e);
        }

        if (BooleanUtils.isTrue((Boolean) e.getIn().getHeader(CLEAN_INPUT_NETEX_ZIP))) {
            try {
                Path directoryPath = Path.of("/tmp/", UUID.randomUUID().toString());
                Path cleanFilepath = Path.of(directoryPath.toString(), inputFilePath.getFileName().toString());
                Files.createDirectories(directoryPath);
                Files.createFile(cleanFilepath);
                try (FileOutputStream fos = new FileOutputStream(cleanFilepath.toFile())) {
                    ZipFileUtils.copyZipFileWithoutUnwantedFiles(inputFilePath, fos, ".xml");
                    e.getMessage().setBody(cleanFilepath.toFile());
                    Files.delete(inputFilePath);
                }
            } catch (IOException exception) {
                LOG.error(exception.getMessage());
            }

        }
    }

}
