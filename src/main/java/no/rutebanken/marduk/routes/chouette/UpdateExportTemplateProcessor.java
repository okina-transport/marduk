package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Utils.CipherEncryption;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.services.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.EXPORT_FROM_TIAMAT;

@Component
public class UpdateExportTemplateProcessor implements Processor {

    private static ExportJsonMapper exportJsonMapper = new ExportJsonMapper();

    @Autowired
    ExportTemplateDAO exportTemplateDAO;

    /**
     * Gets the result stream of an export  and upload it towards database
     * @param exchange
     * @throws Exception
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // get the json export string:
        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        String referential = exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL) != null && (Boolean) exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL) ? "mobiiti_technique" : (String) exchange.getIn().getHeaders().get(CHOUETTE_REFERENTIAL);
        if (StringUtils.isNotBlank(jsonExport)) {
            ExportTemplate export = exportJsonMapper.fromJson(jsonExport);
            Long jobId = (Long) exchange.getIn().getHeaders().get(CHOUETTE_JOB_ID);
            export.setExportJobId(jobId);
            exportTemplateDAO.saveJobId(referential, export);
        }
    }
}

