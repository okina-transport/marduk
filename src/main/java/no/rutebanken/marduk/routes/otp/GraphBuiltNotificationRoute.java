package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.axis.transport.http.HTTPConstants;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

import static no.rutebanken.marduk.Constants.*;

@Component
public class GraphBuiltNotificationRoute extends BaseRouteBuilder {

    @Value("${otp.graph.deployment.notification.url:none}")
    private String otpGraphDeploymentNotificationUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(IOException.class)
                .handled(true)
                .log(LoggingLevel.ERROR, getClass().getName(), "Failed while notifying '" + otpGraphDeploymentNotificationUrl + "' about new graph object.");

        from("direct:notify")
                .setProperty("notificationUrl", constant(otpGraphDeploymentNotificationUrl))
                .choice()
                    .when(exchangeProperty("notificationUrl").isNotEqualTo("none"))
                        .log(LoggingLevel.DEBUG, getClass().getName(), "Notifying " + otpGraphDeploymentNotificationUrl + " about new otp graph")
                        .setHeader(METADATA_DESCRIPTION, constant("Uploaded new Graph object file."))
                        .setHeader(METADATA_FILE, simple("${header." + FILE_HANDLE + "}"))
                        .process(e -> e.getIn().setBody(new Metadata("Uploaded new Graph object file.", e.getIn().getHeader(FILE_HANDLE, String.class), new Date(), Metadata.Status.OK, Metadata.Action.OTP_GRAPH_UPLOAD).getJson()))
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                        .toD("${property.notificationUrl}")
                        .log(LoggingLevel.DEBUG, getClass().getName(), "Done notifying. Got a ${header." + Exchange.HTTP_RESPONSE_CODE + "} back.")
                    .otherwise()
                        .log(LoggingLevel.INFO, getClass().getName(), "No notification url configured for opt graph building. Doing nothing.");

    }
}
