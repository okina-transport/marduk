package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.ExportTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExportTemplateDAO extends RestDAO<ExportTemplate> {

    @Value("${export-templates.api.url}")
    private String exportTemplatesUrl;


    public List<ExportTemplate> getAll(String providerReferential) {
        return super.getEntities(this.exportTemplatesUrl + "?mobiiti_lines=true", providerReferential, ExportTemplate.class);
    }

    public void saveJobId(String providerReferential, ExportTemplate export){
        super.updateEntity(this.exportTemplatesUrl + "/" + export.getId(), providerReferential.replace("mobiiti_",""), ExportTemplate.class, export);
    }


}
