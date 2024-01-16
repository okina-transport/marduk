/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.status;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.EXPORT_TO_CONSUMER_STATUS;

@Component
public class StatusRouteBuilder extends RouteBuilder {

	@Override
	public void configure() throws Exception {
		from("direct:updateStatus")
				.log(LoggingLevel.INFO, getClass().getName(), "Sending off job status event: ${body}")
				.to("activemq:queue:JobEventQueue")
				.routeId("update-status").startupOrder(1);



		from("direct:updateExportToConsumerStatus")
				.process(e->{
					String exportToConsumerStatus = (String) e.getIn().getHeader(EXPORT_TO_CONSUMER_STATUS);
					if (exportToConsumerStatus.equals("OK")){
						JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_TO_CONSUMER).state(JobEvent.State.OK).build();
					}else{
						JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_TO_CONSUMER).state(JobEvent.State.FAILED).build();
					}
				})
				.to("direct:updateStatus")
				.routeId("update-export-to-consumer-status");
	}


}
