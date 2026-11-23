package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.utils.PollJobStatusRoute;
import no.rutebanken.marduk.domain.ExportTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ExportTemplateDAO;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.Status;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_UPDATE_STATUS;

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
                .choice()
                .when(header(POST_PROCESS_FAILED).isNotNull())
                    .to("direct:postProcessFailed")
                .otherwise()
                    .to("direct:postProcessSucceeded")
                .end()
                .routeId("post-process-completed");

        from("direct:postProcessFailed")
                .log(LoggingLevel.ERROR, "PostProcess ${header." + POST_PROCESS + "} failed : ${header." + POST_PROCESS_ERROR_MESSAGE + "}")
                .process(e -> updateExportTemplateStatus(e, Status.FAILED))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_TO_CONSUMER)
                        .type(e.getIn().getHeader(POST_PROCESS_EXPORT_TYPE, String.class))
                        .state(JobEvent.State.FAILED).lugStatus(Status.FAILED).build())
                .to(ROUTE_UPDATE_STATUS)
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(getExportAction(e)).state(JobEvent.State.FAILED)
                        .lugStatus(Status.FAILED)
                        .type(e.getIn().getHeader(POST_PROCESS_EXPORT_TYPE, String.class))
                        .description(e.getIn().getHeader(POST_PROCESS_ERROR_MESSAGE, String.class)).build())
                .to(ROUTE_UPDATE_STATUS)
                .routeId("post-process-failed");

        from("direct:postProcessSucceeded")
                .log(LoggingLevel.INFO, "PostProcess completed")
                .process(e -> {
                    Object netexGlobalRaw = e.getIn().getHeader(NETEX_EXPORT_GLOBAL);
                    Object simulationExpRaw = e.getIn().getHeader(IS_SIMULATION_EXPORT);

                    e.getIn().setHeader(NETEX_EXPORT_GLOBAL, pollJobStatusRoute.convertToBoolean(netexGlobalRaw));
                    e.getIn().setHeader(IS_SIMULATION_EXPORT, pollJobStatusRoute.convertToBoolean(simulationExpRaw));

                    updateExportTemplateStatus(e, Status.FINISHED);

                    JobEvent.Builder event = JobEvent.providerJobBuilder(e);
                    if (e.getIn().getHeader(POST_PROCESS, String.class) != null) {
                        event.lugStatus(Status.FINISHED);
                    }
                    event.timetableAction(JobEvent.TimetableAction.EXPORT_TO_CONSUMER)
                            .state(JobEvent.State.OK)
                            .build();
                })
                .to(ROUTE_UPDATE_STATUS)
                .choice()
                .when(header(JOB_STATUS_JOB_TYPE).isEqualTo("EXPORT_NETEX"))
                    .to("direct:processNetexExportResultEnd")
                .when(header(EXPORT_FROM_TIAMAT).isEqualTo(true))
                    .to("direct:processTiamatExportEnd")
                .otherwise()
                    .to("direct:terminateChouettePostProcess")
                .end()
                .routeId("post-process-succeeded");
    }

    private void updateExportTemplateStatus(Exchange e, Status status) {
        String exportConfigurationId = e.getIn().getHeader(EXPORT_CONFIGURATION_ID, String.class);
        if (exportConfigurationId == null) {
            return;
        }
        Provider provider;
        if(e.getIn().getHeader(PROVIDER_ID, Long.class) == null){
            provider = providerRepository.findByName("mobiiti_technique");
        }
        else{
            provider = providerRepository.getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
        }
        String providerName = provider != null && provider.getChouetteInfo() != null ? provider.getChouetteInfo().getReferential() : null;
        if (StringUtils.isBlank(providerName)) {
            log.warn("No referential found for provider {} : status {} not saved on export template {}", e.getIn().getHeader(PROVIDER_ID), status, exportConfigurationId);
            return;
        }
        ExportTemplate export = exportTemplateDAO.getById(Strings.CS.removeStart(providerName, "mobiiti_"), exportConfigurationId);
        if (export == null) {
            log.warn("Export template {} not found for referential {} : status {} not saved", exportConfigurationId, providerName, status);
            return;
        }
        export.setStatus(status.name());
        exportTemplateDAO.saveExportTemplate(providerName,export);
    }

    private JobEvent.TimetableAction getExportAction(Exchange e) {
        String jobType = e.getIn().getHeader(JOB_STATUS_JOB_TYPE, String.class);
        return JobEvent.TimetableAction.EXPORT_NETEX.name().equals(jobType) ? JobEvent.TimetableAction.EXPORT_NETEX : JobEvent.TimetableAction.EXPORT;
    }
}
