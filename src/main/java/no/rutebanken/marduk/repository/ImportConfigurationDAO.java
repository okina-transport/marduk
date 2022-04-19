package no.rutebanken.marduk.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.rutebanken.marduk.domain.ImportConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImportConfigurationDAO extends RestDAO<ImportConfiguration> {

        @Value("${import-configuration.api.url}")
        private String importConfigurationUrl;


        public ImportConfiguration getImportConfiguration(String providerReferential) {
                return super.getEntity(this.importConfigurationUrl, providerReferential, ImportConfiguration.class);
        }

        public ImportConfiguration update(String providerReferential, ImportConfiguration importConfiguration) throws JsonProcessingException {
                return super.updateEntity(this.importConfigurationUrl + "/" + importConfiguration.getId(), providerReferential, ImportConfiguration.class, importConfiguration);
        }
}
