package no.rutebanken.marduk.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.security.TokenService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
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

        singletonFrom("quartz://marduk/tiamatGetMissingPostCodeQuartz?cron=" + cronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .autoStartup("{{tiamat.get.missing.post.code.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers get missing post code in Tiamat.")
                .to("direct:tiamatGetMissingPostCode")
                .routeId("tiamat-get-missing-post-code-quartz");


        from("direct:tiamatGetMissingPostCode")
                .log(LoggingLevel.INFO, correlation() + "Starting get missing post code in Tiamat")
                .removeHeaders("Camel*")
                .setBody(constant((Object) null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(e -> {
                    String url = tiamatUrl.replace("http4://", "http://") + "/get_missing_postcode";
                    e.setProperty("tiamat_url", url);
                    e.getIn().setHeader("Authorization", "Bearer " + tokenService.getToken());
                })
                .toD("${exchangeProperty.tiamat_url}")
                .log(LoggingLevel.INFO, correlation() + "Completed get missing post code in Tiamat")
                .routeId("tiamat-get-missing-post-code");
    }
}
