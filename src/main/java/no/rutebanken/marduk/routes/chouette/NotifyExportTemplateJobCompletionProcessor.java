package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.routes.chouette.json.Status;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class NotifyExportTemplateJobCompletionProcessor implements Processor {

    private static final ExportJsonMapper exportJsonMapper = new ExportJsonMapper();

    @Autowired
    ExportTemplateDAO exportTemplateDAO;

    @Override
    public void process(Exchange exchange) throws Exception {

        String jsonExport = (String) exchange.getIn().getHeaders().get(CURRENT_EXPORT);
        String referential = BooleanUtils.isTrue((Boolean) exchange.getIn().getHeaders().get(NETEX_EXPORT_GLOBAL)) ?
                "mobiiti_technique" : (String) exchange.getIn().getHeaders().get(CHOUETTE_REFERENTIAL);
        if (StringUtils.isNotBlank(jsonExport)) {
            ExportTemplate export = exportJsonMapper.fromJson(jsonExport);

            Object jobIdObj = exchange.getIn().getHeaders().get(JOB_ID);
            long jobId = 0L;
            if (jobIdObj instanceof Long jobIdLong){
                jobId = jobIdLong;
            } else if (jobIdObj instanceof String jobIdString){
                jobId = Long.parseLong(jobIdString);
            }

            export.setExportJobId(jobId);
            export.setStatus(Status.FINISHED.name());


            exportTemplateDAO.saveExportTemplate(referential, export);
        }
    }
}

