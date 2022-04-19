package no.rutebanken.marduk.routes;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class MyAuthenticator extends Authenticator {
    // This method is called when a password-protected URL is accessed
    private final PasswordAuthentication authentication;

    public MyAuthenticator(String login, String password) {
        authentication = new PasswordAuthentication(login, password.toCharArray());
    }

    protected PasswordAuthentication getPasswordAuthentication() {
        return authentication;
    }
}
