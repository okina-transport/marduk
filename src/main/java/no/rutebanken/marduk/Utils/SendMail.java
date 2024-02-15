package no.rutebanken.marduk.Utils;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Properties;

@Component
public class SendMail {
    @Value("${spring.mail.host:test}")
    private String emailHost;

    @Value("${spring.mail.username:test}")
    private String emailFrom;

    @Value("${spring.mail.port:12}")
    private String emailPort;

    @Value("${spring.mail.password:test}")
    private String emailPassword;

    @Value("${spring.mail.auth:false}")
    private boolean emailAuth;

    @Value("${spring.mail.starttls.enable:false}")
    private boolean emailStartTlsEnable;


    public void sendEmail(String mailObject, String dest, String text, ArrayList<File> attachmentFiles) {

        Properties props = System.getProperties();
        props.put("mail.smtp.host", emailHost);
        props.put("mail.smtp.user", emailFrom);
        props.put("mail.smtp.auth", emailAuth);
        props.put("mail.smtp.starttls.enable", emailStartTlsEnable);
        props.put("mail.smtp.port", emailPort);
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.password", emailPassword);

        Session session = Session.getInstance(props,
                new jakarta.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailFrom, emailPassword);
                    }
                });

        Message msg = new MimeMessage(session);

        try {
            msg.setFrom(new InternetAddress(emailFrom));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(dest));
            msg.setSubject(mailObject);
            msg.setContent(text, "text/html; charset=utf-8");

            if(attachmentFiles != null){
                if(!attachmentFiles.isEmpty()){
                    Multipart multipart = new MimeMultipart();
                    for(File attachementFile: attachmentFiles){
                        addAttachment(multipart, attachementFile);
                    }
                    msg.setContent(multipart);
                }
            }

            Transport transport = session.getTransport("smtp");
            transport.connect(emailHost, emailFrom, emailPassword);
            Transport.send(msg);
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private void addAttachment(Multipart multipart, File file) throws MessagingException {
        DataSource source = new FileDataSource(file);
        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setDataHandler(new DataHandler(source));
        messageBodyPart.setFileName(file.getName());
        multipart.addBodyPart(messageBodyPart);
    }
}
