package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Utils.CipherEncryption;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.services.BlobStoreService;
import no.rutebanken.marduk.services.FtpService;
import no.rutebanken.marduk.services.RestUploadService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CURRENT_EXPORT;
import static no.rutebanken.marduk.Constants.NETEX_EXPORT_GLOBAL;

@Component
public class ExportToConsumersProcessor implements Processor {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${marduk.upload.public.path:/tmp}")
    private String publicUploadPath;

//    @Autowired
    private static ExportJsonMapper exportJsonMapper = new ExportJsonMapper();

    @Autowired
    FtpService ftpService;

    @Autowired
    RestUploadService restUploadService;

    @Autowired
    CipherEncryption cipherEncryption;

    @Autowired
    BlobStoreService blobStoreService;

    /**
     * Gets the result stream of an export  and upload it towards consumers defined for this export
     * @param exchange
     * @throws Exception
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // get the json export string:
        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        String referential = (Boolean) exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL) ? "mobiiti_technique" : (String) exchange.getIn().getHeaders().get(CHOUETTE_REFERENTIAL);
        if (StringUtils.isNotBlank(jsonExport)) {
            ExportTemplate export = exportJsonMapper.fromJson(jsonExport);
            log.info("Found " + export.getConsumers().size() + " for export " + export.getId() + "/" + export.getName());
            String filePath = export.getExportedFileName() != null ? export.getExportedFileName()  : (String) exchange.getIn().getHeaders().get("fileName");
            InputStream streamToUpload = (InputStream) exchange.getIn().getBody();
            export.getConsumers().stream().forEach(consumer -> {
                log.info(consumer.getType() + " consumer upload starting : " + consumer.getName() + " => " + consumer.getServiceUrl());
                try {
                    String passwordDecryptedConsumer = null;
                    if(consumer.getPassword() != null && consumer.getPassword().length > 0){
                        passwordDecryptedConsumer = cipherEncryption.decrypt(consumer.getPassword());
                    }

                    String secretKeyDecryptedConsumer = null;
                    if(consumer.getSecretKey() != null && consumer.getSecretKey().length > 0){
                        secretKeyDecryptedConsumer = cipherEncryption.decrypt(consumer.getSecretKey());
                    }

                    switch (consumer.getType()) {
                        case FTP:
                            ftpService.uploadStream(streamToUpload, consumer.getServiceUrl(), consumer.getLogin(), passwordDecryptedConsumer, consumer.getPort(), consumer.getDestinationPath(), filePath);
                            break;
                        case SFTP:
                            ftpService.uploadStreamSFTP(streamToUpload, consumer.getServiceUrl(), consumer.getLogin(), passwordDecryptedConsumer, consumer.getPort(), consumer.getDestinationPath(), filePath);
                            break;
                        case REST:
                            restUploadService.uploadStream(streamToUpload, consumer.getServiceUrl(), filePath, consumer.getLogin(), secretKeyDecryptedConsumer);
                            break;
                        case URL:
                            blobStoreService.uploadBlob("/" + publicUploadPath + "/" + referential + "/" + filePath, true, streamToUpload);
                            break;
                    }
                    log.info(consumer.getType() + " consumer upload completed " + consumer.getName() + " => " + consumer.getServiceUrl());
                } catch (Exception e) {
                    log.error("Error while uploading to consumer " + consumer.toString(), e);
                }
            });
        }

    }

    public static Optional<ExportTemplate> currentExport(Exchange exchange) throws IOException {
        ExportTemplate export = null;
        // get the json export string:
        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        if (StringUtils.isNotBlank(jsonExport)) {
            export = exportJsonMapper.fromJson(jsonExport);
        }
        return Optional.ofNullable(export);
    }


}
