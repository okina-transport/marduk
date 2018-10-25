package no.rutebanken.marduk.routes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.swing.*;
import java.util.*;

public class SendDataAlertExpired {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.host}")
    private String emailHost;

    @Value("${spring.mail.username}")
    private String emailFrom;

    @Value("${spring.mail.port}")
    private String emailPort;

    @Value("${spring.mail.password}")
    private String emailPassword;

    @Value("${spring.properties.mail.smtp.auth}")
    private boolean emailAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
    private boolean emailStartTlsEnable;


    public void prepareEmail(Map<String, Object> list) {

        StringBuilder textFuturExpired = new StringBuilder("<h2>Liste des espaces de données avec des calendriers bientôt expirés: </h2>");
        StringBuilder textNowExpired = new StringBuilder("<h2>Liste des espaces de données avec des calendriers expirés: </h2>");

        HashMap<String, ?> mapValidityCategories;
        ArrayList<Map<String, ?>> arrayDetails = new ArrayList<>();
        ArrayList<String> listLines = new ArrayList<>();

        JTextPane jtp = new JTextPane();

        for (Map.Entry<String, Object> provider : list.entrySet()) {
            mapValidityCategories = new HashMap<>((Map<? extends String, ?>) provider.getValue());
            for (Map.Entry<String, ?> details : mapValidityCategories.entrySet()) {
                if (details.getKey().equals("validityCategories")) {
                    arrayDetails.addAll((Collection) details.getValue());
                    for (Map<String, ?> listId : arrayDetails) {
                        for (Map.Entry<String, ?> id : listId.entrySet()) {
                            if(id.getKey().equals("lineNumbers")){
                                listLines = (ArrayList<String>) id.getValue();
                            }
                            if (id.getValue().equals("EXPIRING") && listLines.size() != 0) {
                                buildMail(textFuturExpired, listLines, provider);
                            }

                            if (id.getValue().equals("INVALID") && listLines.size() != 0) {
                                buildMail(textNowExpired, listLines, provider);
                            }
                            listLines.clear();
                        }
                    }
                    arrayDetails.clear();
                }
            }
            mapValidityCategories.clear();
        }

        String textHtml = textFuturExpired.toString() + textNowExpired.toString();
        jtp.setText(textHtml);
        sendEmail(textHtml);
    }

    private void buildMail(StringBuilder text, ArrayList<String> listLines, Map.Entry<String, Object> provider) {
        if (listLines.size() != 0) {
            text.append("</br>");
            text.append("- ");
            text.append(provider.getKey());
            text.append(":");
            text.append("</br>");
            text.append("Lignes: ");
            for (String lineId : listLines) {
                text.append("</br>");
                text.append("- ");
                text.append(lineId);
            }
        }
    }

    public void sendEmail(String text) {
        /* L'adresse IP de votre serveur SMTP */
        String smtpServer = "smtp.okina.fr";


        /* L'adresse de l'expéditeur */
        String from = "webmaster@okina.fr";

        String password = "webOKINA2%";

        /* L'adresse du destinataire */
        //TODO mettre rmr@okina.fr
        String to = "gfora@okina.fr";

        /* L'objet du message */
        String objet = "Liste des espaces de donnees ayant des calendriers prochainement expires ou expires";

        Properties props = System.getProperties();
        props.put("mail.smtp.host", smtpServer);
        props.put("mail.smtp.user", from);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.port", "587");
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.password", password);

        /* Session encapsule pour un client donné sa connexion avec le serveur de mails.*/
        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(from, password);
                    }
                });

        /* Création du message*/
        Message msg = new MimeMessage(session);

        try {
            msg.setFrom(new InternetAddress(from));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(objet);
            msg.setContent(text, "text/html; charset=utf-8");
            Transport transport = session.getTransport("smtp");
            transport.connect(smtpServer, from, password);
            Transport.send(msg);
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        new SendDataAlertExpired().prepareEmail();
    }

}
