package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

@Component
public class ChouettePresetExportsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    private MultipleExportProcessor multipleExportProcessor;


    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ExportTemplateDAO exportTemplateDAO;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteExportAll").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette all export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    log.info("chouetteExportAll : starting predefined exports");
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
//                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                    Provider provider = providerRepository.getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    List<ExportTemplate> exports = exportTemplateDAO.getAll(provider.getChouetteInfo().getReferential());

                    // get the matching migration mosaic provider to target export
                    Long mosaicProviderId = provider.getChouetteInfo().getMigrateDataToProvider();
                    Provider mosaicProvider = providerRepository.getProvider(mosaicProviderId);


                    log.info("Found export templates " + exports.size());
                    e.getOut().setBody(exports);
                    e.getOut().setHeaders(e.getIn().getHeaders());
                    e.getOut().getHeaders().put(CHOUETTE_REFERENTIAL, mosaicProvider.chouetteInfo.getReferential());
                    e.getOut().getHeaders().put(PROVIDER_ID, mosaicProvider.getId());
                    e.getOut().getHeaders().put("providerId", mosaicProvider.getId());

                    e.getOut().getHeaders().put(ORIGINAL_PROVIDER_ID, provider.getId());
                })
                .process(multipleExportProcessor)
//                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-export-all-job");
    }


}
