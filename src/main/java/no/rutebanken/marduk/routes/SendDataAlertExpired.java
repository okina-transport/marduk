package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Utils.SendMail;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


/**
 * Send mail to notify expired lines.
 */

@Component
public class SendDataAlertExpired {

    @Autowired
    private SendMail sendMail;

    /**
     * Retrieving expiring and invalid lines from the list of data producers.
     * @param list
     */

    public void prepareEmailDataAlertExpired(Map<String, Object> list, String producer, String emailDest) {
        String mailObject = "MOBIITI : Calendriers prochainement expires ou expires dans " + producer;
        String textHtml = "";
        HashMap<String, ?> mapValidity;

        for (Map.Entry<String, Object> provider : list.entrySet()) {
            mapValidity = new HashMap<>((Map<? extends String, ?>) provider.getValue());
            for (Map.Entry<String, ?> details : mapValidity.entrySet()) {
                if (details.getKey().equals("invalid")) {
                    if(details.getValue().equals(true)) {
                        textHtml = "Votre offre " + producer + " est expirée.";
                    }
                }
                else if (details.getKey().equals("expiring")){
                    if(details.getValue().equals(true)){
                        textHtml = "Votre offre " + producer + " expirera dans moins de 30 jours.";
                    }
                }
            }
            mapValidity.clear();
        }

        if(StringUtils.isNotEmpty(textHtml)){
            sendMail.sendEmail(mailObject, emailDest, textHtml, null);
        }
    }

}
