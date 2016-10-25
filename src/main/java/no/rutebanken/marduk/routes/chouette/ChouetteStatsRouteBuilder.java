package no.rutebanken.marduk.routes.chouette;

import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;


@Component
public class ChouetteStatsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteGetStats")
                .process( e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                // TODO validity categories should be extracted as a config parameter
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/statistics/${header." + CHOUETTE_REFERENTIAL + "}/line?minDaysValidityCategory=120&minDaysValidityCategory=127"))
                .toD("${exchangeProperty.chouette_url}");
    }


}
