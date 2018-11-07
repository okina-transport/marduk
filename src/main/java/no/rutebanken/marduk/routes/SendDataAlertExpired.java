package no.rutebanken.marduk.routes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;


/**
 * Send mail to notify expired lines.
 */

@Component
public class SendDataAlertExpired {

    @Value("${spring.mail.host}")
    private String emailHost;

    @Value("${spring.mail.username}")
    private String emailFrom;

    @Value("${spring.mail.to}")
    private String emailTo;

    @Value("${spring.mail.port}")
    private String emailPort;

    @Value("${spring.mail.password}")
    private String emailPassword;

    @Value("${spring.mail.auth}")
    private boolean emailAuth;

    @Value("${spring.mail.starttls.enable}")
    private boolean emailStartTlsEnable;

    private Map<String, String> producers = new HashMap<>();



    /**
     * Retrieving expiring and invalid lines from the list of data producers.
     * @param list
     */

    public void prepareEmail(Map<String, Object> list) {

        StringBuilder textFuturExpired = new StringBuilder("<p style='font-weight: bold;'>Liste des espaces de données avec des calendriers expirés dans moins de 30 jours: <p>");
        StringBuilder textNowExpired = new StringBuilder("<br><p style='font-weight: bold;'>Liste des espaces de données avec des calendriers expirés: </p>");

        HashMap<String, ?> mapValidity;

        boolean dataExpiring = false;
        boolean dataInvalid = false;

        producersNames();

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

        sendEmail(textHtml);
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
                text.append(producers.get(provider.getKey()));
                text.append("</p>");
        }
    }


    /**
     * Send mail with the information of the lines.
     * @param text
     */

    public void sendEmail(String text) {

        String objet = "Liste des espaces de donnees ayant des calendriers prochainement expires ou expires";

        Properties props = System.getProperties();
        props.put("mail.smtp.host", emailHost);
        props.put("mail.smtp.user", emailFrom);
        props.put("mail.smtp.auth", emailAuth);
        props.put("mail.smtp.starttls.enable", emailStartTlsEnable);
        props.put("mail.smtp.port", emailPort);
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.password", emailPassword);

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailFrom, emailPassword);
                    }
                });

        Message msg = new MimeMessage(session);

        try {
            msg.setFrom(new InternetAddress(emailFrom));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailTo));
            msg.setSubject(objet);
            msg.setContent(text, "text/html; charset=utf-8");
            Transport transport = session.getTransport("smtp");
            transport.connect(emailHost, emailFrom, emailPassword);
            Transport.send(msg);
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    /**
     * List of identifiers and names of producers of data for display in the mail.
     */

    private void producersNames(){

        //AOM
        producers.put("bme","Bordeaux Métropole");
        producers.put("bri","CA Bassin de Brive");
        producers.put("chl","CA Grand Châtellerault");
        producers.put("ang","CA Grand Angoulême");
        producers.put("cog","CA Grand Cognac");
        producers.put("lro","CA La Rochelle");
        producers.put("lim","CA Limoges Métropole");
        producers.put("nio","CA du Niortais");
        producers.put("pau","CA Pau Béarn Pyrénées");
        producers.put("roc","CA Rochefort Océan");
        producers.put("car","Ca Royan Atlantique");
        producers.put("tut","CA Tulle");
        producers.put("cou","CA Grand Dax");
        producers.put("per","CA Grand Périgueux");
        producers.put("vit","Grand Poitiers");
        producers.put("yeg","MACS");
        producers.put("ber","CA Bergeracoise");
        producers.put("age","CA Agen");
        producers.put("vdg","CA Val de Garonne");
        producers.put("bda","COBAS");
        producers.put("vil","CA du Grand Villeneuvois");
        producers.put("lib","CA du Libournais");
        producers.put("mdm","CA du Marsan");
        producers.put("pba","CA Pays Basque");
        producers.put("gue","CA Grand Guéret");
        producers.put("bbr","CA Bocage Bressuirais");
        producers.put("ole","CdC Ile d'Oléron");
        producers.put("sai","CA Saintes");

        //Sites territorialisés
        producers.put("cha","Charente");
        producers.put("cma","Charente-Maritime");
        producers.put("cor","Corrèze");
        producers.put("cre","Creuse");
        producers.put("dse","Deux-Sèvres");
        producers.put("dor","Dordogne");
        producers.put("gir","Gironde");
        producers.put("hvi","Haute-Vienne");
        producers.put("lan","Landes");
        producers.put("lga","Lot-et-Garonne");
        producers.put("pat","Pyrénées-Atlantiques");
        producers.put("vie","Vienne");
        producers.put("bac","Liaisons Maritime Gironde");
        producers.put("fai","Aix-Fouras");
        producers.put("snc","SNCF");
    }

}
