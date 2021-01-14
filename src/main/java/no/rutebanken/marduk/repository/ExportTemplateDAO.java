package no.rutebanken.marduk.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.ExportTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Component
public class ExportTemplateDAO extends RestDAO<ExportTemplate> {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${export-templates.api.url}")
    private String exportTemplatesUrl;


    public List<ExportTemplate> getAll(String providerReferential) {
        return super.getEntities(this.exportTemplatesUrl + "?mobiiti_lines=true", providerReferential, ExportTemplate.class);
    }


}
