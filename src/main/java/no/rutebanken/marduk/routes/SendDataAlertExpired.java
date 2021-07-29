package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Utils.SendMail;
import no.rutebanken.marduk.domain.Provider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Send mail to notify expired lines.
 */

@Component
public class SendDataAlertExpired {

    @Autowired
    private SendMail sendMail;

    /**
     * Retrieving expiring and invalid lines from the list of data producers.
     *
     * @param list
     */

    public void prepareEmailDataAlertExpired(Map<String, Object> list, Collection<Provider> providerList) {
        String textHtml = "";
        HashMap<String, ?> mapValidity;

        for (Map.Entry<String, Object> provider : list.entrySet()) {
            boolean dataExpiring = false;
            boolean dataInvalid = false;
            mapValidity = new HashMap<>((Map<? extends String, ?>) provider.getValue());
            for (Map.Entry<String, ?> details : mapValidity.entrySet()) {
                if (details.getKey().equals("invalid")) {
                    if (details.getValue().equals(true)) {
                        textHtml = "Votre offre " + provider.getKey() + " est expirée.";
                        dataInvalid = true;
                    }
                } else if (details.getKey().equals("expiring")) {
                    if (details.getValue().equals(true)) {
                        textHtml = "Votre offre " + provider.getKey() + " expirera dans moins de 60 jours.";
                        dataExpiring = true;
                    }
                }
            }

            if (dataExpiring || dataInvalid) {
                String mailObject = "MOBIITI : Calendriers prochainement expires ou expires dans " + provider.getKey();
                Optional<String> emailDest = providerList.stream()
                        .filter(providerFromList -> provider.getKey().equals(providerFromList.name) &&
                                !providerFromList.name.contains("mobiiti") &&
                                !providerFromList.name.contains("technique") &&
                                StringUtils.isNotEmpty(providerFromList.getEmail()))
                        .map(Provider::getEmail)
                        .findFirst();

                if (emailDest.isPresent()) {
                    sendMail.sendEmail(mailObject, emailDest.get(), textHtml, null);
                }
            }
            mapValidity.clear();
        }
    }

}
