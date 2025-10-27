package no.rutebanken.marduk.services.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.AbstractMap;

import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Headers.UPLOAD_INPUT_CONTENT_TYPE;
import static no.rutebanken.marduk.utils.constants.RouteParamsConstants.Headers.UPLOAD_INPUT_FILENAME;

@Component
public class MultiPartProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiPartProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        LOGGER.debug("Converting file from request");
        MultipartFile multipartFile = (MultipartFile) ((AbstractMap.SimpleImmutableEntry) exchange.getMessage().getBody()).getValue();
        exchange.getIn().setHeader(UPLOAD_INPUT_FILENAME, multipartFile.getOriginalFilename());
        exchange.getIn().setHeader(UPLOAD_INPUT_CONTENT_TYPE, multipartFile.getContentType());
        exchange.getMessage().setBody(multipartFile.getInputStream());
    }
}
