package no.rutebanken.marduk.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.rutebanken.marduk.domain.ConfigurationFtp;
import no.rutebanken.marduk.domain.ConfigurationUrl;
import no.rutebanken.marduk.domain.ImportConfiguration;
import no.rutebanken.marduk.repository.ImportConfigurationDAO;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.ANALYZE_ACTION;

@Component
public class ImportRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    static ImportConfigurationDAO importConfigurationDAO;

    public static void updateLastTimestamp(Exchange e)throws JsonProcessingException {
        String referential = (String) e.getIn().getHeader(CHOUETTE_REFERENTIAL);
        String fileName = (String) e.getIn().getHeader(FILE_NAME);
        String importConfigurationId = (String) e.getIn().getHeader(IMPORT_CONFIGURATION_ID);

        ImportConfiguration importConfiguration = importConfigurationDAO.getImportConfiguration(referential, importConfigurationId);

        // Vérification pour les configurations FTP
        List<ConfigurationFtp> ftpList = importConfiguration.getConfigurationFtpList().isEmpty() ? new ArrayList<>() : importConfiguration.getConfigurationFtpList();
        updateLastTimestampIfFileNameMatchesForFtp(ftpList, fileName);

        // Vérification pour les configurations URL
        List<ConfigurationUrl> urlList = importConfiguration.getConfigurationUrlList();
        updateLastTimestampIfFileNameMatchesForUrl(urlList, fileName);

        importConfigurationDAO.update(referential, importConfiguration);
    }
    // Méthode pour mettre à jour lastTimestamp si le fichier correspond (pour les configurations FTP)
    private static void updateLastTimestampIfFileNameMatchesForFtp(List<ConfigurationFtp> ftpList, String fileName) {
        ftpList.stream().filter(config -> config.getFilename().equals(fileName))
                .findFirst().ifPresent(config -> config.setLastTimestamp(null));
    }

    // Méthode pour mettre à jour lastTimestamp si le fichier correspond (pour les configurations URL)
    private static void updateLastTimestampIfFileNameMatchesForUrl(List<ConfigurationUrl> urlList, String fileName) {
        urlList.stream().filter(config -> config.getUrl().substring(config.getUrl().lastIndexOf('/') + 1).equals(fileName))
                .findFirst().ifPresent(config -> config.setLastTimestamp(null));
    }

    public static JobEvent.TimetableAction getTimeTableAction(Exchange e) {
        Boolean analyze = e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) != null ? e.getIn().getHeader(ANALYZE_ACTION, Boolean.class) : false;
        return analyze ? JobEvent.TimetableAction.FILE_ANALYZE : JobEvent.TimetableAction.IMPORT;
    }
}
