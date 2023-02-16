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

    @Value("${chouette.predefined.exports.mobiiti.technique.provider.schedule:0+0+20+?+*+MON-FRI}")
    private String chouettePredefinedExportsMobiitiTechniqueProviderCronSchedule;


    @Value("${parking.predefined.exports.mobiiti.technique.schedule:0+0+20+?+*+MON-FRI}")
    private String parkingPredefinedExportsMobiitiTechniqueCronSchedule;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:predefinedExports?transacted=true").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette all export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    log.info("predefinedExports : starting predefined exports");
                    Provider provider;
                    if(e.getIn().getHeader(PROVIDER_ID, Long.class) == null){
                        provider = providerRepository.findByName("mobiiti_technique");
                    }
                    else{
                        provider = providerRepository.getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    }

                    Provider mobiitiProvider;

                    if (provider.name.contains(superspaceName)) {
                        mobiitiProvider = provider;
                        provider = providerRepository.findByName(provider.name.replace(superspaceName + "_", ""));
                    } else {
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


        singletonFrom("quartz2://marduk/chouettePredefinedExportsMobiitiTechniqueProviderCronSchedule?cron=" + chouettePredefinedExportsMobiitiTechniqueProviderCronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .autoStartup("{{chouette.predefined.exports.mobiiti.technique.provider.autoStartup:true}}")
                .transacted()
                .filter(e -> shouldQuartzRouteTrigger(e, chouettePredefinedExportsMobiitiTechniqueProviderCronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers predefined exports mobiiti technique provider in Chouette.")
                .inOnly("activemq:queue:predefinedExports")
                .routeId("chouette-predefined-export-mobiiti_technique-quartz");


        singletonFrom("quartz2://marduk/parkingPredefinedExportsMobiitiTechniqueCronSchedule?cron=" + parkingPredefinedExportsMobiitiTechniqueCronSchedule + "&trigger.timeZone=" + Constants.TIME_ZONE)
                .autoStartup("{{parking.predefined.exports.mobiiti.technique.autoStartup:true}}")
                .transacted()
                .filter(e -> shouldQuartzRouteTrigger(e, parkingPredefinedExportsMobiitiTechniqueCronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers predefined export for parkings.")
                .process(e -> {
                    Provider provider = providerRepository.findByName("mobiiti_technique");
                    e.getOut().getHeaders().put(PROVIDER_ID, provider.getId());
                })
                .inOnly("activemq:queue:TiamatParkingsExport")
                .routeId("parkings-predefined-export-mobiiti_technique-quartz");

    }


}
