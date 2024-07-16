package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class UpdateExportTemplateProcessor implements Processor {

    private static final ExportJsonMapper exportJsonMapper = new ExportJsonMapper();

    @Autowired
    ExportTemplateDAO exportTemplateDAO;

    /**
     * Gets the result stream of an export and upload it towards database
     * @param exchange
     * @throws Exception
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // get the json export string:
        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        String referential = BooleanUtils.isTrue((Boolean) exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL)) ?
                "mobiiti_technique" : (String) exchange.getIn().getHeaders().get(CHOUETTE_REFERENTIAL);
        if (StringUtils.isNotBlank(jsonExport)) {
            ExportTemplate export = exportJsonMapper.fromJson(jsonExport);

            Object jobIdObj = exchange.getIn().getHeaders().get(JOB_ID);
            Long jobId = 0L;
            if (jobIdObj instanceof Long){
                jobId = (Long) jobIdObj;
            }else if (jobIdObj instanceof String){
                jobId = Long.parseLong((String) jobIdObj);
            }

            export.setExportJobId(jobId);
            exportTemplateDAO.saveJobId(referential, export);
        }
    }
}

