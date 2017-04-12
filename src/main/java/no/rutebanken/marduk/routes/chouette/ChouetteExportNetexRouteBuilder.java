package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.getLastPathElementOfUrl;

@Component
public class ChouetteExportNetexRouteBuilder extends AbstractChouetteRouteBuilder {
	@Value("${chouette.url}")
	private String chouetteUrl;

	@Value("${chouette.export.days.forward:365}")
	private int daysForward;

	@Value("${chouette.export.days.back:365}")
	private int daysBack;

	@Override
	public void configure() throws Exception {
		super.configure();

		from("activemq:queue:ChouetteExportNetexQueue?transacted=true").streamCaching()
				.transacted()
				.log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette Netex export for provider with id ${header." + PROVIDER_ID + "}")
				.process(e -> {
					// Add correlation id only if missing
					e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
					e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
				})
				.process(e -> Status.builder(e).action(Status.Action.EXPORT_NETEX).state(Status.State.PENDING).build())
				.to("direct:updateStatus")

				.process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
				.process(e -> e.getIn().setHeader(JSON_PART, Parameters.getNetexExportProvider(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))))) //Using header to addToExchange json data
				.log(LoggingLevel.INFO, correlation() + "Creating multipart request")
				.process(e -> toGenericChouetteMultipart(e))
				.setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
				.toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/netexprofile")
				.process(e -> {
					e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
					e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
				})
				.setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNetexExportResult"))
				.setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(Status.Action.EXPORT_NETEX.name()))
				.removeHeader("loopCounter")
				.to("activemq:queue:ChouettePollStatusQueue")
				.routeId("chouette-start-export-netex");


		from("direct:processNetexExportResult")
				.choice()
				.when(simple("${header.action_report_result} == 'OK'"))
				.log(LoggingLevel.INFO, correlation() + "Export ok")
				.log(LoggingLevel.DEBUG, correlation() + "Calling url ${header.data_url}")
				.removeHeaders("Camel*")
				.setBody(simple(""))
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
				.toD("${header.data_url}")

				.setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
				.setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
				.to("direct:uploadBlob")
				.process(e -> Status.builder(e).action(Status.Action.EXPORT_NETEX).state(Status.State.OK).build())
				.endChoice()
				.when(simple("${header.action_report_result} == 'NOK'"))
				.log(LoggingLevel.WARN, correlation() + "Netex export failed")
				.process(e -> Status.builder(e).action(Status.Action.EXPORT_NETEX).state(Status.State.FAILED).build())
				.otherwise()
				.log(LoggingLevel.ERROR, correlation() + "Something went wrong on Netex export")
				.process(e -> Status.builder(e).action(Status.Action.EXPORT_NETEX).state(Status.State.FAILED).build())
				.end()
				.to("direct:updateStatus")
				.removeHeader(Constants.CHOUETTE_JOB_ID)
				.routeId("chouette-process-export-netex-status");
	}
}