package no.rutebanken.marduk.services;

import org.junit.Test;

import java.io.IOException;

public class NotificationServiceTest {

    NotificationService notificationService = new NotificationService();

    @Test
    public void testSendNotification() throws IOException {
        notificationService.sendNotification("https://httpbin.org/post");
    }
}