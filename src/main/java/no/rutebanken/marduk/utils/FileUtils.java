package no.rutebanken.marduk.utils;

import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Headers.UPLOAD_INPUT_FILENAME;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Headers.UPLOAD_INPUT_FILEPATH;

public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    private FileUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Path createTemporaryFile(Exchange exchange) {
        Path path;
        String inputFileName = exchange.getIn().getHeader(UPLOAD_INPUT_FILENAME, String.class);
        if (StringUtils.isNotBlank(inputFileName)) {
            path = Path.of("/tmp/" + inputFileName);
        } else {
            path = Path.of("/tmp/" + UUID.randomUUID() + ".zip");
        }
        exchange.getIn().setHeader(UPLOAD_INPUT_FILEPATH, path.toAbsolutePath().toString());
        try (InputStream is = exchange.getIn().getBody(InputStream.class)) {
            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("File created at: {}", path.toAbsolutePath());
            exchange.getIn().setBody(path.toFile());
        } catch (IOException exception) {
            throw new MardukException("Failed to parse File multipart content: " + exception.getMessage());
        }
        return path;
    }
}
