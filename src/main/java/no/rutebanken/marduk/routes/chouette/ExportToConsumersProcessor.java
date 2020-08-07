package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.services.FtpService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.CURRENT_EXPORT;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Component
public class ExportToConsumersProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ExportJsonMapper exportJsonMapper;

    @Autowired
    FtpService ftpService;

    @Override
    public void process(Exchange exchange) throws Exception {
        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        ExportTemplate export = exportJsonMapper.fromJson(jsonExport);
        log.info("Found " + export.getConsumers().size() + " for export " + export.getId() + "/" + export.getName());
        String filePath = (String) exchange.getIn().getHeaders().get(FILE_HANDLE);

        InputStream streamToUpload = (InputStream) exchange.getIn().getBody();
//        File fileToUpload = new File(filePath);
//        log.info("Uploading file  " + filePath + " to consumers ");
        export.getConsumers().stream().forEach(consumer -> {
            log.info(consumer.getType() + " consumer upload starting " + consumer.getName() + " => " + consumer.getServiceUrl());
            try {
                switch (consumer.getType()) {
                    case FTP:
//                        ftpService.uploadFile(fileToUpload, consumer.getServiceUrl(), consumer.getLogin(), consumer.getPassword());
                        ftpService.uploadStream(streamToUpload, consumer.getServiceUrl(), consumer.getLogin(), consumer.getPassword(), filePath);
                        break;
                    case REST:
                        log.info("No REST consumer processing for now for " + consumer.getName());
                        break;
                }
            } catch (Exception e) {
                log.error("Error while uploading to consumer " + consumer.toString(), e);
            }
            log.info(consumer.getType() + " consumer upload completed " + consumer.getName() + " => " + consumer.getServiceUrl());
        });
    }


}
