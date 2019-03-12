package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Utils.SendMail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;


/**
 * Send mail to notify expired lines.
 */

@Component
public class SendDataAlertExpired {

    @Autowired
    private SendMail sendMail;

    @Autowired
    private Producers producers;


    /**
     * Retrieving expiring and invalid lines from the list of data producers.
     * @param list
     */

    public void prepareEmailDataAlertExpired(Map<String, Object> list) {

        StringBuilder textFuturExpired = new StringBuilder("<p style='font-weight: bold;'>Liste des espaces de données avec des calendriers expirés dans moins de 30 jours: <p>");
        StringBuilder textNowExpired = new StringBuilder("<br><p style='font-weight: bold;'>Liste des espaces de données avec des calendriers expirés: </p>");

        HashMap<String, ?> mapValidity;

        boolean dataExpiring = false;
        boolean dataInvalid = false;


        for (Map.Entry<String, Object> provider : list.entrySet()) {
            mapValidity = new HashMap<>((Map<? extends String, ?>) provider.getValue());
            for (Map.Entry<String, ?> details : mapValidity.entrySet()) {
                if (details.getKey().equals("invalid")) {
                    if(details.getValue().equals(true)) {
                        formatMail(textNowExpired, provider);
                        dataInvalid = true;
                    }
                }
                else if (details.getKey().equals("expiring")){
                    if(details.getValue().equals(true)){
                        formatMail(textFuturExpired, provider);
                        dataExpiring = true;
                    }
                }
            }
            mapValidity.clear();
        }

        if(!dataExpiring){
            textFuturExpired.append("<p>Aucun producteur avec des calendriers expirés sous 30 jours.</p>");
        }

        if(!dataInvalid){
            textNowExpired.append("<p>Aucun producteur avec des calendriers expirés.</p>");
        }

        String textHtml = textFuturExpired.toString() + textNowExpired.toString();

        String mailObject = "Liste des espaces de donnees ayant des calendriers prochainement expires ou expires";

        sendMail.sendEmail(mailObject, textHtml, null);
    }


    /**
     * Adding the number of line in the text
     * @param text
     * @param provider
     */

    private void formatMail(StringBuilder text, Map.Entry<String, Object> provider) {
        if (!provider.getKey().contains("naq_")) {
                text.append("<p>");
                text.append("- ");
                text.append(producers.producersListName().get(provider.getKey()));
                text.append("</p>");
        }
    }

}
