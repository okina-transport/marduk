package no.rutebanken.marduk.services;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@Disabled
class NotificationServiceTest {

    NotificationService notificationService = new NotificationService();

    @Test
    void testSendNotification() throws IOException {
        notificationService.sendNotification("https://httpbin.org/post");
    }
}