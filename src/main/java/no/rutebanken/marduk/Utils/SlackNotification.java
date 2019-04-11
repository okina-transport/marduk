package no.rutebanken.marduk.Utils;

/**
 * Created by tgonzalez on 22/04/16.
 * Service en charge des publications
 */

import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;


@Component
public class SlackNotification {

    Logger log;

    final String POST = "POST";
    final String PAYLOAD = "payload=";
    final String UTF_8 = "UTF-8";

    final String service = "https://hooks.slack.com/services/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";
    final int timeout = 5000;

    public static final String NOTIFICATION_CHANNEL = "notifications";

    /**
     * Envoyer une notification slack respectant le format JSON demandé
     * Exemple :  "{"text": "Compagnie *RDTL*", "channel": "#general", "username": "Formulaire AOT/Exploitant"}"
     * @param jsonSlack
     * @return
     */
    public boolean sendSlackNotificationJson(String jsonSlack) {
        return postNotification(jsonSlack);
    }

    /**
     * Envoyer une notification slack avec un nom et un message
     * @param title Exemple : Formulaire d'inscription
     * @param message
     * @return
     */
    public boolean sendSlackNotificationTitleAndMessage(String title, String message) {
        return postNotification("{\"text\": \"" +message+ "\", \"channel\": \"#general\", \"username\": \"" +title+ "\"}");
    }


	/**
	 * Envoyer une notification slack avec un nom et un message dans un channel
	 * @param title Exemple : Formulaire d'inscription
	 * @param message
	 * @return
	 */
	public boolean sendSlackNotificationTitleAndMessage(String channel, String title, String message) {
		return postNotification("{\"text\": \"" +message+ "\", \"channel\": \"" + channel+ "\", \"username\": \"" +title+ "\"}");
	}

    /**
     * Méthode d'envoi de la notification
     * @param jsonSlack
     * @return
     */
    private boolean postNotification(String jsonSlack) {
        HttpURLConnection connection = null;
        try {
            // Create connection
            final URL url = new URL(service);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(POST);
            connection.setConnectTimeout(timeout);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            final String payload = PAYLOAD
                    + URLEncoder.encode(jsonSlack, UTF_8);

            // Send request
            final DataOutputStream wr = new DataOutputStream(
                    connection.getOutputStream());
            wr.writeBytes(payload);
            wr.flush();
            wr.close();

            // Get Response
            final InputStream is = connection.getInputStream();
            final BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\n');
            }

            rd.close();
            return ("ok".equals(response.toString()));
        } catch (Exception e) {
            log.error("Impossible d'envoyer une notification slack : "+e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
