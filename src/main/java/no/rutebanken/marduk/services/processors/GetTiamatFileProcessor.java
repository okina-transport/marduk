package no.rutebanken.marduk.services.processors;

import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;


@Component
public class GetTiamatFileProcessor  implements Processor {

    @Autowired
    FileSystemService fileSystemService;


    @Override
    public void process(Exchange exchange) throws Exception {
        File file = fileSystemService.getTiamatFile(exchange);
        FileSystemResource fsr = new FileSystemResource(file);
        exchange.getIn().setBody(fsr.getInputStream());
    }
}
