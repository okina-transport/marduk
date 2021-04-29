package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.ORIGINAL_PROVIDER_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Component
public class PredefinedExportsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Autowired
    private MultipleExportProcessor multipleExportProcessor;


    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ExportTemplateDAO exportTemplateDAO;

    @Value("${superspace.name}")
    private String superspaceName;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:predefinedExports").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette all export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    log.info("predefinedExports : starting predefined exports");
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    Provider provider = providerRepository.getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    Provider mobiitiProvider;

                    if(provider.name.contains(superspaceName)) {
                        mobiitiProvider = provider;
                        provider = providerRepository.findByName(provider.name.replace(superspaceName+"_", ""));
                    }
                    else {
                        Long mobiitiProviderId = provider.getChouetteInfo().getMigrateDataToProvider();
                        mobiitiProvider = providerRepository.getProvider(mobiitiProviderId);
                    }

                    // get the matching migration mobiiti provider to target export
                    List<ExportTemplate> exports = exportTemplateDAO.getAll(provider.getChouetteInfo().getReferential());

                    log.info("Found export templates " + exports.size());
                    e.getOut().setBody(exports);
                    e.getOut().setHeaders(e.getIn().getHeaders());
                    e.getOut().getHeaders().put(CHOUETTE_REFERENTIAL, mobiitiProvider.chouetteInfo.getReferential());
                    e.getOut().getHeaders().put(PROVIDER_ID, mobiitiProvider.getId());
                    e.getOut().getHeaders().put("providerId", mobiitiProvider.getId());

                    e.getOut().getHeaders().put(ORIGINAL_PROVIDER_ID, provider.getId());
                })
                .process(multipleExportProcessor)
                .routeId("chouette-send-export-all-job");
    }


}
