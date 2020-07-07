package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.ExportTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExportTemplateDAO extends RestDAO<ExportTemplate> {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${export-templates.api.url}")
    private String exportTemplatesUrl;


    public List<ExportTemplate> getAll(String providerSchema) {
        return super.getEntities(this.exportTemplatesUrl);
    }


}
