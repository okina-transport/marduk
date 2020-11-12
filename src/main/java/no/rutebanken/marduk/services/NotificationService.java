package no.rutebanken.marduk.services;

import no.rutebanken.marduk.Constants;
import org.apache.camel.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NotificationService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public void sendNotification(@Header(value = Constants.NOTIFICATION_URL) String notificationUrl) throws IOException {

        HttpPost post = new HttpPost(notificationUrl);
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            if(response.getStatusLine().getStatusCode() == 200){
                logger.info("Notification url OK : " + response.getStatusLine().getStatusCode());
            }
            else {
                logger.error("Notification url KO : " + response.getStatusLine().getStatusCode());
            }

        }
        catch (Exception e){
            logger.error("Impossible d'envoyer une notification sur l'url : " + notificationUrl + " Erreur: " + e);
        }

    }


}
