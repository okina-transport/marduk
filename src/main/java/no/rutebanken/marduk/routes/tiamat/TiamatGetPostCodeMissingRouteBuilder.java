package no.rutebanken.marduk.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.security.TokenService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TiamatGetPostCodeMissingRouteBuilder extends BaseRouteBuilder {

    @Value("${cron.get.missing.post.code}")
    private String cronSchedule;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Autowired
    TokenService tokenService;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/tiamatGetMissingPostCodeQuartz?cron=" + cronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .autoStartup("{{tiamat.get.missing.post.code.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers get missing post code in Tiamat.")
                .to("direct:tiamatGetMissingPostCode")
                .routeId("tiamat-get-missing-post-code-quartz");


        from("direct:tiamatGetMissingPostCode")
                .log(LoggingLevel.INFO, correlation() + "Starting get missing post code in Tiamat")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setHeader("Authorization", constant("Bearer " + tokenService.getToken()))
                .toD(tiamatUrl + "/get_missing_postcode")
                .log(LoggingLevel.INFO, correlation() + "Completed get missing post code in Tiamat")
                .routeId("tiamat-get-missing-post-code");
    }
}
