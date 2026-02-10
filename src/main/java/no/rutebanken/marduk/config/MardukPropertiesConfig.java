package no.rutebanken.marduk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "marduk")
public class MardukPropertiesConfig {

    private boolean gtfsImportFilterLocationTypeEnabled = false;

    public boolean isGtfsImportFilterLocationTypeEnabled() {
        return gtfsImportFilterLocationTypeEnabled;
    }

    public void setGtfsImportFilterLocationTypeEnabled(boolean gtfsImportFilterLocationTypeEnabled) {
        this.gtfsImportFilterLocationTypeEnabled = gtfsImportFilterLocationTypeEnabled;
    }
}
