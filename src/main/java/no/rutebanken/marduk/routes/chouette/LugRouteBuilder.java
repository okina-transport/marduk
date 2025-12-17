package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Utils.PollJobStatusRoute;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.Status;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class LugRouteBuilder extends BaseRouteBuilder {


    private int maxConsumers = 5;

    @Autowired
    PollJobStatusRoute pollJobStatusRoute;


    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ExportTemplateDAO exportTemplateDAO;

    @Override
    public void configure() throws Exception {
        from("jms:queue:PostProcessCompleted?transacted=true&maxConcurrentConsumers=" + maxConsumers)
                .log(LoggingLevel.INFO, "PostProcess completed")
                .process(e -> {
                    Object netexGlobalRaw = e.getIn().getHeader(NETEX_EXPORT_GLOBAL);
                    Object simulationExpRaw = e.getIn().getHeader(IS_SIMULATION_EXPORT);

                    e.getIn().setHeader(NETEX_EXPORT_GLOBAL, pollJobStatusRoute.convertToBoolean(netexGlobalRaw));
                    e.getIn().setHeader(IS_SIMULATION_EXPORT, pollJobStatusRoute.convertToBoolean(simulationExpRaw));

                    String exportConfigurationId = e.getIn().getHeader(EXPORT_CONFIGURATION_ID, String.class);
                    if (exportConfigurationId != null) {
                        Provider provider;
                        if(e.getIn().getHeader(PROVIDER_ID, Long.class) == null){
                            provider = providerRepository.findByName("mobiiti_technique");
                        }
                        else{
                            provider = providerRepository.getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                        }
                        String providerName = provider.getChouetteInfo().getReferential();
                        ExportTemplate export = exportTemplateDAO.getById(providerName.replace("mobiiti_",""), e.getIn().getHeader(EXPORT_CONFIGURATION_ID, String.class));
                        export.setStatus(Status.FINISHED.name());
                        exportTemplateDAO.saveExportTemplate(providerName,export);

                    }
                })
                .choice()
                .when(header(JOB_STATUS_JOB_TYPE).isEqualTo("EXPORT_NETEX"))
                    .to("direct:processNetexExportResultEnd")
                .when(header(EXPORT_FROM_TIAMAT).isEqualTo(true))
                    .to("direct:processTiamatExportEnd")
                .otherwise()
                    .to("direct:terminateChouettePostProcess")
                .end()
                .routeId("post-process-completed");
    }
}
